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
import com.example.kintai.attendance.infrastructure.jooq.generated.tables.records.AttendanceSummariesRecord;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static com.example.kintai.attendance.infrastructure.jooq.generated.Tables.ATTENDANCE_SUMMARIES;

/**
 * 日次勤怠サマリープロジェクター — ドメインイベントを購読して attendance_summaries を更新する
 *
 * <p>8種類のドメインイベントを {@code @TransactionalEventListener} で購読し、
 * Read Model（attendance_summaries）を jOOQ で UPSERT / UPDATE する。
 * 元トランザクションのコミット後に新しいトランザクションで実行される。</p>
 *
 * <p>設計書: 30_設計/データベース/勤怠記録.md の「リードモデル同期方式」に対応</p>
 */
@Component
public class AttendanceSummaryProjector {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSummaryProjector.class);
    private static final String SYSTEM_USER = "system";
    /** プロジェクトの TZ 方針に従い、Instant → OffsetDateTime 変換は Asia/Tokyo で行う */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Tokyo");

    /** jOOQ DSLContext — 型安全な SQL アクセス */
    private final DSLContext dsl;

    /** 月次サマリー再集計用 Projector */
    private final MonthlySummaryProjector monthlySummaryProjector;

    public AttendanceSummaryProjector(DSLContext dsl,
                                      MonthlySummaryProjector monthlySummaryProjector) {
        this.dsl = dsl;
        this.monthlySummaryProjector = monthlySummaryProjector;
    }

    // ========================================
    // 出勤打刻イベント → サマリー作成（UPSERT）
    // ========================================

    /**
     * 出勤打刻イベントを処理する
     *
     * <p>attendance_summaries に行を UPSERT し、ステータス = CLOCKED_IN、出勤時刻を記録する。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ClockedInEvent event) {
        log.debug("ClockedInEvent受信: attendanceId={}", event.attendanceRecordId().value());

        OffsetDateTime occurredAt = toOffsetDateTime(event.occurredAt());
        OffsetDateTime now = OffsetDateTime.now(APP_ZONE);
        OffsetDateTime clockInTime = toOffsetDateTime(event.clockTime().value());

        dsl.insertInto(ATTENDANCE_SUMMARIES)
                .set(ATTENDANCE_SUMMARIES.ATTENDANCE_ID, event.attendanceRecordId().value())
                .set(ATTENDANCE_SUMMARIES.EMPLOYEE_ID, event.employeeId().value())
                .set(ATTENDANCE_SUMMARIES.WORK_DATE, event.workDate().value())
                .set(ATTENDANCE_SUMMARIES.STATUS, "CLOCKED_IN")
                .set(ATTENDANCE_SUMMARIES.CLOCK_IN_TIME, clockInTime)
                .set(ATTENDANCE_SUMMARIES.LAST_EVENT_AT, occurredAt)
                .set(ATTENDANCE_SUMMARIES.EVENT_COUNT, 1)
                .set(ATTENDANCE_SUMMARIES.CREATED_AT, now)
                .set(ATTENDANCE_SUMMARIES.UPDATED_AT, now)
                .set(ATTENDANCE_SUMMARIES.CREATED_BY, SYSTEM_USER)
                .set(ATTENDANCE_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .onConflict(ATTENDANCE_SUMMARIES.ATTENDANCE_ID)
                .doUpdate()
                .set(ATTENDANCE_SUMMARIES.STATUS, "CLOCKED_IN")
                .set(ATTENDANCE_SUMMARIES.CLOCK_IN_TIME, clockInTime)
                .set(ATTENDANCE_SUMMARIES.LAST_EVENT_AT, occurredAt)
                .set(ATTENDANCE_SUMMARIES.EVENT_COUNT, ATTENDANCE_SUMMARIES.EVENT_COUNT.plus(1))
                .set(ATTENDANCE_SUMMARIES.UPDATED_AT, now)
                .set(ATTENDANCE_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .execute();
    }

    // ========================================
    // 退勤打刻イベント → ステータス・退勤時刻更新
    // ========================================

    /**
     * 退勤打刻イベントを処理する
     *
     * <p>ステータス = CLOCKED_OUT、退勤時刻を設定。退勤時点で休憩中フラグを false にする。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ClockedOutEvent event) {
        log.debug("ClockedOutEvent受信: attendanceId={}", event.attendanceRecordId().value());

        int updated = dsl.update(ATTENDANCE_SUMMARIES)
                .set(ATTENDANCE_SUMMARIES.STATUS, "CLOCKED_OUT")
                .set(ATTENDANCE_SUMMARIES.CLOCK_OUT_TIME, toOffsetDateTime(event.clockTime().value()))
                .set(ATTENDANCE_SUMMARIES.IS_ON_BREAK, false)
                .set(ATTENDANCE_SUMMARIES.LAST_EVENT_AT, toOffsetDateTime(event.occurredAt()))
                .set(ATTENDANCE_SUMMARIES.EVENT_COUNT, ATTENDANCE_SUMMARIES.EVENT_COUNT.plus(1))
                .set(ATTENDANCE_SUMMARIES.UPDATED_AT, OffsetDateTime.now(APP_ZONE))
                .set(ATTENDANCE_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .where(ATTENDANCE_SUMMARIES.ATTENDANCE_ID.eq(event.attendanceRecordId().value()))
                .execute();

        warnIfNotFound(updated, event.attendanceRecordId().value());
    }

    // ========================================
    // 休憩開始イベント → 休憩中フラグを true
    // ========================================

    /**
     * 休憩開始イベントを処理する
     *
     * <p>休憩中フラグを true にし、イベント数を加算する。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BreakStartedEvent event) {
        log.debug("BreakStartedEvent受信: attendanceId={}", event.attendanceRecordId().value());

        int updated = dsl.update(ATTENDANCE_SUMMARIES)
                .set(ATTENDANCE_SUMMARIES.IS_ON_BREAK, true)
                .set(ATTENDANCE_SUMMARIES.LAST_EVENT_AT, toOffsetDateTime(event.occurredAt()))
                .set(ATTENDANCE_SUMMARIES.EVENT_COUNT, ATTENDANCE_SUMMARIES.EVENT_COUNT.plus(1))
                .set(ATTENDANCE_SUMMARIES.UPDATED_AT, OffsetDateTime.now(APP_ZONE))
                .set(ATTENDANCE_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .where(ATTENDANCE_SUMMARIES.ATTENDANCE_ID.eq(event.attendanceRecordId().value()))
                .execute();

        warnIfNotFound(updated, event.attendanceRecordId().value());
    }

    // ========================================
    // 休憩終了イベント → 休憩時間を加算、フラグ false
    // ========================================

    /**
     * 休憩終了イベントを処理する
     *
     * <p>休憩中フラグを false にし、今回の休憩時間を累計に加算する（複数回の休憩に対応）。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BreakEndedEvent event) {
        log.debug("BreakEndedEvent受信: attendanceId={}, breakMinutes={}",
                event.attendanceRecordId().value(), event.breakMinutes());

        int updated = dsl.update(ATTENDANCE_SUMMARIES)
                .set(ATTENDANCE_SUMMARIES.IS_ON_BREAK, false)
                // 既存の break_minutes + 今回の休憩時間（SQLの自己参照で加算する）
                .set(ATTENDANCE_SUMMARIES.BREAK_MINUTES,
                        ATTENDANCE_SUMMARIES.BREAK_MINUTES.plus(event.breakMinutes()))
                .set(ATTENDANCE_SUMMARIES.LAST_EVENT_AT, toOffsetDateTime(event.occurredAt()))
                .set(ATTENDANCE_SUMMARIES.EVENT_COUNT, ATTENDANCE_SUMMARIES.EVENT_COUNT.plus(1))
                .set(ATTENDANCE_SUMMARIES.UPDATED_AT, OffsetDateTime.now(APP_ZONE))
                .set(ATTENDANCE_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .where(ATTENDANCE_SUMMARIES.ATTENDANCE_ID.eq(event.attendanceRecordId().value()))
                .execute();

        warnIfNotFound(updated, event.attendanceRecordId().value());
    }

    // ========================================
    // 勤務時間計算完了 → 全時間フィールド更新 + 月次再集計
    // ========================================

    /**
     * 勤務時間計算完了イベントを処理する
     *
     * <p>WorkDuration（勤務時間4項目）と OvertimeDuration（残業時間4項目）を上書き更新する。
     * その後、月次サマリーの再集計をトリガーする。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(WorkDurationCalculatedEvent event) {
        log.debug("WorkDurationCalculatedEvent受信: attendanceId={}", event.attendanceRecordId().value());

        WorkDuration wd = event.workDuration();
        OvertimeDuration od = event.overtimeDuration();

        // UPDATE ... RETURNING で employee_id, work_date を取得し、月次再集計に渡す
        AttendanceSummariesRecord updated = dsl.update(ATTENDANCE_SUMMARIES)
                .set(ATTENDANCE_SUMMARIES.SCHEDULED_MINUTES, wd.scheduledMinutes())
                .set(ATTENDANCE_SUMMARIES.ACTUAL_MINUTES, wd.actualMinutes())
                .set(ATTENDANCE_SUMMARIES.BREAK_MINUTES, wd.breakMinutes())
                .set(ATTENDANCE_SUMMARIES.NET_WORK_MINUTES, wd.netWorkMinutes())
                .set(ATTENDANCE_SUMMARIES.REGULAR_OVERTIME_MINUTES, od.regularOvertimeMinutes())
                .set(ATTENDANCE_SUMMARIES.LATE_NIGHT_MINUTES, od.lateNightMinutes())
                .set(ATTENDANCE_SUMMARIES.HOLIDAY_MINUTES, od.holidayMinutes())
                .set(ATTENDANCE_SUMMARIES.TOTAL_OVERTIME_MINUTES, od.totalOvertimeMinutes())
                .set(ATTENDANCE_SUMMARIES.LAST_EVENT_AT, toOffsetDateTime(event.occurredAt()))
                .set(ATTENDANCE_SUMMARIES.EVENT_COUNT, ATTENDANCE_SUMMARIES.EVENT_COUNT.plus(1))
                .set(ATTENDANCE_SUMMARIES.UPDATED_AT, OffsetDateTime.now(APP_ZONE))
                .set(ATTENDANCE_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .where(ATTENDANCE_SUMMARIES.ATTENDANCE_ID.eq(event.attendanceRecordId().value()))
                .returning(ATTENDANCE_SUMMARIES.EMPLOYEE_ID, ATTENDANCE_SUMMARIES.WORK_DATE)
                .fetchOne();

        if (updated == null) {
            log.warn("サマリーが見つかりません: attendanceId={}", event.attendanceRecordId().value());
            return;
        }

        // 日次サマリー更新後に月次サマリーを再集計する
        monthlySummaryProjector.recalculate(updated.getEmployeeId(), updated.getWorkDate());
    }

    // ========================================
    // 打刻修正イベント → 該当の打刻時刻を修正
    // ========================================

    /**
     * 打刻修正イベントを処理する
     *
     * <p>CLOCK_IN / CLOCK_OUT の対象に応じて時刻フィールドを修正後の値で上書きする。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ClockCorrectedEvent event) {
        log.debug("ClockCorrectedEvent受信: attendanceId={}, targetType={}",
                event.attendanceRecordId().value(), event.targetType());

        OffsetDateTime afterTime = toOffsetDateTime(event.afterTime().value());
        OffsetDateTime occurredAt = toOffsetDateTime(event.occurredAt());
        OffsetDateTime now = OffsetDateTime.now(APP_ZONE);

        // 修正対象の打刻種別に応じて UPDATE 対象カラムを切り替える
        var update = dsl.update(ATTENDANCE_SUMMARIES)
                .set(ATTENDANCE_SUMMARIES.LAST_EVENT_AT, occurredAt)
                .set(ATTENDANCE_SUMMARIES.EVENT_COUNT, ATTENDANCE_SUMMARIES.EVENT_COUNT.plus(1))
                .set(ATTENDANCE_SUMMARIES.UPDATED_AT, now)
                .set(ATTENDANCE_SUMMARIES.UPDATED_BY, SYSTEM_USER);
        if (event.targetType() == ClockType.CLOCK_IN) {
            update = update.set(ATTENDANCE_SUMMARIES.CLOCK_IN_TIME, afterTime);
        } else if (event.targetType() == ClockType.CLOCK_OUT) {
            update = update.set(ATTENDANCE_SUMMARIES.CLOCK_OUT_TIME, afterTime);
        }

        int updated = update
                .where(ATTENDANCE_SUMMARIES.ATTENDANCE_ID.eq(event.attendanceRecordId().value()))
                .execute();

        warnIfNotFound(updated, event.attendanceRecordId().value());
    }

    // ========================================
    // 手動勤務登録イベント → サマリー作成（UPSERT）
    // ========================================

    /**
     * 手動勤務登録イベントを処理する
     *
     * <p>出退勤打刻がない場合の手動補完。サマリーを UPSERT し、
     * 出勤・退勤時刻とステータス（CLOCKED_OUT）を設定する。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ManualAttendanceRegisteredEvent event) {
        log.debug("ManualAttendanceRegisteredEvent受信: attendanceId={}", event.attendanceRecordId().value());

        OffsetDateTime occurredAt = toOffsetDateTime(event.occurredAt());
        OffsetDateTime now = OffsetDateTime.now(APP_ZONE);
        OffsetDateTime startTime = toOffsetDateTime(event.startTime().value());
        OffsetDateTime endTime = toOffsetDateTime(event.endTime().value());

        dsl.insertInto(ATTENDANCE_SUMMARIES)
                .set(ATTENDANCE_SUMMARIES.ATTENDANCE_ID, event.attendanceRecordId().value())
                .set(ATTENDANCE_SUMMARIES.EMPLOYEE_ID, event.employeeId().value())
                .set(ATTENDANCE_SUMMARIES.WORK_DATE, event.workDate().value())
                .set(ATTENDANCE_SUMMARIES.STATUS, "CLOCKED_OUT")
                .set(ATTENDANCE_SUMMARIES.CLOCK_IN_TIME, startTime)
                .set(ATTENDANCE_SUMMARIES.CLOCK_OUT_TIME, endTime)
                .set(ATTENDANCE_SUMMARIES.LAST_EVENT_AT, occurredAt)
                .set(ATTENDANCE_SUMMARIES.EVENT_COUNT, 1)
                .set(ATTENDANCE_SUMMARIES.CREATED_AT, now)
                .set(ATTENDANCE_SUMMARIES.UPDATED_AT, now)
                .set(ATTENDANCE_SUMMARIES.CREATED_BY, SYSTEM_USER)
                .set(ATTENDANCE_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .onConflict(ATTENDANCE_SUMMARIES.ATTENDANCE_ID)
                .doUpdate()
                .set(ATTENDANCE_SUMMARIES.STATUS, "CLOCKED_OUT")
                .set(ATTENDANCE_SUMMARIES.CLOCK_IN_TIME, startTime)
                .set(ATTENDANCE_SUMMARIES.CLOCK_OUT_TIME, endTime)
                .set(ATTENDANCE_SUMMARIES.LAST_EVENT_AT, occurredAt)
                .set(ATTENDANCE_SUMMARIES.EVENT_COUNT, ATTENDANCE_SUMMARIES.EVENT_COUNT.plus(1))
                .set(ATTENDANCE_SUMMARIES.UPDATED_AT, now)
                .set(ATTENDANCE_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .execute();
    }

    // ========================================
    // 勤怠確定イベント → ステータスを FINALIZED + 月次再集計
    // ========================================

    /**
     * 勤怠確定イベントを処理する
     *
     * <p>月次本締めで勤怠記録が確定された際に、ステータスを FINALIZED に変更し
     * 月次サマリーの再集計をトリガーする。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AttendanceFinalizedEvent event) {
        log.debug("AttendanceFinalizedEvent受信: attendanceId={}", event.attendanceRecordId().value());

        AttendanceSummariesRecord updated = dsl.update(ATTENDANCE_SUMMARIES)
                .set(ATTENDANCE_SUMMARIES.STATUS, "FINALIZED")
                .set(ATTENDANCE_SUMMARIES.IS_ON_BREAK, false)
                .set(ATTENDANCE_SUMMARIES.LAST_EVENT_AT, toOffsetDateTime(event.occurredAt()))
                .set(ATTENDANCE_SUMMARIES.EVENT_COUNT, ATTENDANCE_SUMMARIES.EVENT_COUNT.plus(1))
                .set(ATTENDANCE_SUMMARIES.UPDATED_AT, OffsetDateTime.now(APP_ZONE))
                .set(ATTENDANCE_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .where(ATTENDANCE_SUMMARIES.ATTENDANCE_ID.eq(event.attendanceRecordId().value()))
                .returning(ATTENDANCE_SUMMARIES.EMPLOYEE_ID, ATTENDANCE_SUMMARIES.WORK_DATE)
                .fetchOne();

        if (updated == null) {
            log.warn("サマリーが見つかりません: attendanceId={}", event.attendanceRecordId().value());
            return;
        }

        // 確定後に月次サマリーを再集計する
        monthlySummaryProjector.recalculate(updated.getEmployeeId(), updated.getWorkDate());
    }

    // ========================================
    // ヘルパー
    // ========================================

    /** UPDATE 行数が 0 の場合は対象が見つからなかった旨を warn ログに出す */
    private void warnIfNotFound(int updatedRows, UUID attendanceId) {
        if (updatedRows == 0) {
            log.warn("サマリーが見つかりません: attendanceId={}", attendanceId);
        }
    }

    /** Instant を Asia/Tokyo の OffsetDateTime に変換する */
    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(APP_ZONE).toOffsetDateTime();
    }
}
