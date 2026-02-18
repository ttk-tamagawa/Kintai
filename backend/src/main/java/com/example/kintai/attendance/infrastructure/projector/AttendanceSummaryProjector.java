package com.example.kintai.attendance.infrastructure.projector;

import com.example.kintai.attendance.domain.model.ClockType;
import com.example.kintai.attendance.domain.model.OvertimeDuration;
import com.example.kintai.attendance.domain.model.WorkDuration;
import com.example.kintai.attendance.domain.model.event.AttendanceFinalizedEvent;
import com.example.kintai.attendance.domain.model.event.BreakEndedEvent;
import com.example.kintai.attendance.domain.model.event.BreakStartedEvent;
import com.example.kintai.attendance.domain.model.event.ClockCorrectedEvent;
import com.example.kintai.attendance.domain.model.event.ClockedInEvent;
import com.example.kintai.attendance.domain.model.event.ClockedOutEvent;
import com.example.kintai.attendance.domain.model.event.ManualAttendanceRegisteredEvent;
import com.example.kintai.attendance.domain.model.event.WorkDurationCalculatedEvent;
import com.example.kintai.attendance.infrastructure.persistence.entity.AttendanceSummaryJpaEntity;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 日次勤怠サマリープロジェクター — ドメインイベントを購読してattendance_summariesを更新する
 *
 * <p>8種類のドメインイベントを {@code @TransactionalEventListener} で購読し、
 * Read Model（attendance_summaries）をUPSERTする。
 * 元トランザクションのコミット後に新しいトランザクションで実行される。</p>
 *
 * <p>設計書: 30_設計/データベース/勤怠記録.md の「リードモデル同期方式」に対応</p>
 */
@Component
public class AttendanceSummaryProjector {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSummaryProjector.class);
    private static final String SYSTEM_USER = "system";

    private final EntityManager entityManager;
    private final MonthlySummaryProjector monthlySummaryProjector;

    public AttendanceSummaryProjector(EntityManager entityManager,
                                      MonthlySummaryProjector monthlySummaryProjector) {
        this.entityManager = entityManager;
        this.monthlySummaryProjector = monthlySummaryProjector;
    }

    // ========================================
    // 出勤打刻イベント → サマリー作成（UPSERT）
    // ========================================

    /**
     * 出勤打刻イベントを処理する
     *
     * <p>attendance_summariesに新規行を作成（UPSERT）し、
     * ステータスをCLOCKED_INに設定、出勤時刻を記録する。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ClockedInEvent event) {
        log.debug("ClockedInEvent受信: attendanceId={}", event.attendanceRecordId().value());

        // サマリーを取得または新規作成する
        AttendanceSummaryJpaEntity entity = findOrCreate(
                event.attendanceRecordId().value(),
                event.employeeId().value(),
                event.workDate().value()
        );

        // ステータスと出勤時刻を設定する
        entity.setStatus("CLOCKED_IN");
        entity.setClockInTime(event.clockTime().value());

        // イベント追跡情報を更新する
        updateEventTracking(entity, event.occurredAt());

        entityManager.merge(entity);
    }

    // ========================================
    // 退勤打刻イベント → ステータス・退勤時刻更新
    // ========================================

    /**
     * 退勤打刻イベントを処理する
     *
     * <p>ステータスをCLOCKED_OUTに変更し、退勤時刻を記録する。
     * 勤務時間の計算はWorkDurationCalculatedEventで別途処理される。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ClockedOutEvent event) {
        log.debug("ClockedOutEvent受信: attendanceId={}", event.attendanceRecordId().value());

        // 既存のサマリーを取得する（出勤打刻時に作成済み）
        AttendanceSummaryJpaEntity entity = findExisting(event.attendanceRecordId().value());
        if (entity == null) return;

        // ステータスと退勤時刻を設定する
        entity.setStatus("CLOCKED_OUT");
        entity.setClockOutTime(event.clockTime().value());

        // イベント追跡情報を更新する
        updateEventTracking(entity, event.occurredAt());
    }

    // ========================================
    // 休憩開始イベント → イベント数のみ更新
    // ========================================

    /**
     * 休憩開始イベントを処理する
     *
     * <p>イベント数を加算する。休憩時間はBreakEndedEventで更新される。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BreakStartedEvent event) {
        log.debug("BreakStartedEvent受信: attendanceId={}", event.attendanceRecordId().value());

        AttendanceSummaryJpaEntity entity = findExisting(event.attendanceRecordId().value());
        if (entity == null) return;

        // イベント追跡情報のみ更新する
        updateEventTracking(entity, event.occurredAt());
    }

    // ========================================
    // 休憩終了イベント → 休憩時間を加算
    // ========================================

    /**
     * 休憩終了イベントを処理する
     *
     * <p>今回の休憩時間を累計に加算する。複数回の休憩に対応するため加算方式。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BreakEndedEvent event) {
        log.debug("BreakEndedEvent受信: attendanceId={}, breakMinutes={}",
                event.attendanceRecordId().value(), event.breakMinutes());

        AttendanceSummaryJpaEntity entity = findExisting(event.attendanceRecordId().value());
        if (entity == null) return;

        // 今回の休憩時間を累計に加算する
        entity.setBreakMinutes(entity.getBreakMinutes() + event.breakMinutes());

        // イベント追跡情報を更新する
        updateEventTracking(entity, event.occurredAt());
    }

    // ========================================
    // 勤務時間計算完了 → 全時間フィールド更新
    // ========================================

    /**
     * 勤務時間計算完了イベントを処理する
     *
     * <p>WorkDuration（勤務時間4項目）とOvertimeDuration（残業時間4項目）の
     * 全フィールドを上書き更新する。その後、月次サマリーの再集計をトリガーする。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(WorkDurationCalculatedEvent event) {
        log.debug("WorkDurationCalculatedEvent受信: attendanceId={}",
                event.attendanceRecordId().value());

        AttendanceSummaryJpaEntity entity = findExisting(event.attendanceRecordId().value());
        if (entity == null) return;

        // WorkDuration（勤務時間4項目）を設定する
        WorkDuration wd = event.workDuration();
        entity.setScheduledMinutes(wd.scheduledMinutes());
        entity.setActualMinutes(wd.actualMinutes());
        entity.setBreakMinutes(wd.breakMinutes());
        entity.setNetWorkMinutes(wd.netWorkMinutes());

        // OvertimeDuration（残業時間4項目）を設定する
        OvertimeDuration od = event.overtimeDuration();
        entity.setRegularOvertimeMinutes(od.regularOvertimeMinutes());
        entity.setLateNightMinutes(od.lateNightMinutes());
        entity.setHolidayMinutes(od.holidayMinutes());
        entity.setTotalOvertimeMinutes(od.totalOvertimeMinutes());

        // イベント追跡情報を更新する
        updateEventTracking(entity, event.occurredAt());

        // 日次サマリー更新後に月次サマリーを再集計する
        monthlySummaryProjector.recalculate(entity.getEmployeeId(), entity.getWorkDate());
    }

    // ========================================
    // 打刻修正イベント → 該当の打刻時刻を修正
    // ========================================

    /**
     * 打刻修正イベントを処理する
     *
     * <p>修正対象の打刻種別（CLOCK_IN/CLOCK_OUT）に応じて、
     * 該当の時刻フィールドを修正後の値で上書きする。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ClockCorrectedEvent event) {
        log.debug("ClockCorrectedEvent受信: attendanceId={}, targetType={}",
                event.attendanceRecordId().value(), event.targetType());

        AttendanceSummaryJpaEntity entity = findExisting(event.attendanceRecordId().value());
        if (entity == null) return;

        // 修正対象の打刻種別に応じて時刻を更新する
        if (event.targetType() == ClockType.CLOCK_IN) {
            entity.setClockInTime(event.afterTime().value());
        } else if (event.targetType() == ClockType.CLOCK_OUT) {
            entity.setClockOutTime(event.afterTime().value());
        }

        // イベント追跡情報を更新する
        updateEventTracking(entity, event.occurredAt());
    }

    // ========================================
    // 手動勤務登録イベント → サマリー作成（UPSERT）
    // ========================================

    /**
     * 手動勤務登録イベントを処理する
     *
     * <p>出退勤の打刻がない場合の手動補完。サマリーをUPSERTし、
     * 出勤・退勤時刻とステータスを設定する。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ManualAttendanceRegisteredEvent event) {
        log.debug("ManualAttendanceRegisteredEvent受信: attendanceId={}",
                event.attendanceRecordId().value());

        // サマリーを取得または新規作成する
        AttendanceSummaryJpaEntity entity = findOrCreate(
                event.attendanceRecordId().value(),
                event.employeeId().value(),
                event.workDate().value()
        );

        // 手動登録の出勤・退勤時刻とステータスを設定する
        entity.setStatus("CLOCKED_OUT");
        entity.setClockInTime(event.startTime().value());
        entity.setClockOutTime(event.endTime().value());

        // イベント追跡情報を更新する
        updateEventTracking(entity, event.occurredAt());

        entityManager.merge(entity);
    }

    // ========================================
    // 勤怠確定イベント → ステータスをFINALIZEDに変更
    // ========================================

    /**
     * 勤怠確定イベントを処理する
     *
     * <p>月次本締めにより勤怠記録が確定された際に、
     * ステータスをFINALIZEDに変更する。月次サマリーの再集計もトリガーする。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AttendanceFinalizedEvent event) {
        log.debug("AttendanceFinalizedEvent受信: attendanceId={}",
                event.attendanceRecordId().value());

        AttendanceSummaryJpaEntity entity = findExisting(event.attendanceRecordId().value());
        if (entity == null) return;

        // ステータスをFINALIZEDに変更する
        entity.setStatus("FINALIZED");

        // イベント追跡情報を更新する
        updateEventTracking(entity, event.occurredAt());

        // 確定後に月次サマリーを再集計する
        monthlySummaryProjector.recalculate(entity.getEmployeeId(), entity.getWorkDate());
    }

    // ========================================
    // ヘルパーメソッド
    // ========================================

    /**
     * attendance_summariesからエンティティを取得する。見つからない場合は新規作成する
     *
     * <p>CLOCKED_INイベントやMANUAL_REGISTEREDイベントで使用する。
     * 初回イベントではサマリーが存在しないため新規作成する。</p>
     */
    private AttendanceSummaryJpaEntity findOrCreate(UUID attendanceId, UUID employeeId,
                                                     LocalDate workDate) {
        AttendanceSummaryJpaEntity entity = entityManager.find(
                AttendanceSummaryJpaEntity.class, attendanceId);

        if (entity == null) {
            // 初回イベント: 新規サマリーを作成する
            entity = new AttendanceSummaryJpaEntity(attendanceId, employeeId, workDate);
            log.debug("新規サマリー作成: attendanceId={}", attendanceId);
        }

        return entity;
    }

    /**
     * attendance_summariesから既存エンティティを取得する
     *
     * <p>出勤打刻後のイベント（退勤・休憩・計算等）で使用する。
     * サマリーが見つからない場合はログ出力してnullを返す。</p>
     */
    private AttendanceSummaryJpaEntity findExisting(UUID attendanceId) {
        AttendanceSummaryJpaEntity entity = entityManager.find(
                AttendanceSummaryJpaEntity.class, attendanceId);

        if (entity == null) {
            log.warn("サマリーが見つかりません: attendanceId={}", attendanceId);
        }

        return entity;
    }

    /**
     * イベント追跡情報（最終イベント日時・イベント数・更新日時）を更新する
     */
    private void updateEventTracking(AttendanceSummaryJpaEntity entity, Instant occurredAt) {
        entity.setLastEventAt(occurredAt);
        entity.setEventCount(entity.getEventCount() + 1);
        entity.setUpdatedAt(Instant.now());
        entity.setUpdatedBy(SYSTEM_USER);
    }
}
