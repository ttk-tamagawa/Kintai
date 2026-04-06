package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.*;
import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.repository.AttendanceEventRepository;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.domain.service.WorkDurationCalculator;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.MonthlyClosingId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.DomainEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

/**
 * 勤怠記録ユースケース — 勤怠記録の7つの書き込みユースケースを統合するアプリケーションサービス
 *
 * <p>各コマンドメソッドは以下の共通フローで処理する:
 * <ol>
 *   <li>集約（AttendanceRecord）をリポジトリから取得する（または新規作成する）</li>
 *   <li>集約のコマンドメソッドを呼び出す（ドメインロジック実行）</li>
 *   <li>必要に応じて勤務時間を計算する（ドメインサービス）</li>
 *   <li>集約をリポジトリに保存する（打刻エントリもリポジトリ内で自動保存される）</li>
 *   <li>ドメインイベントをイベントストアに記録する（JSONB形式）</li>
 *   <li>ドメインイベントを発行する（プロジェクターがRead Modelを更新する）</li>
 * </ol>
 * </p>
 *
 * <p>設計書: 30_設計/API/勤怠記録.md, 20_ユースケース/コマンド/ に対応</p>
 *
 * <p>対応ユースケース:
 * <ul>
 *   <li>UC-ATT-001: 出勤打刻する（clockIn）</li>
 *   <li>UC-ATT-002: 退勤打刻する（clockOut）</li>
 *   <li>UC-ATT-003: 休憩開始する（startBreak）</li>
 *   <li>UC-ATT-004: 休憩終了する（endBreak）</li>
 *   <li>UC-ATT-005: 打刻を修正する（correctClock）</li>
 *   <li>UC-ATT-006: 勤務実績を登録する（registerManualAttendance）</li>
 *   <li>UC-ATT-007: 本締め確定する（finalizeRecord）</li>
 * </ul>
 * </p>
 */
@Service
@Transactional
public class AttendanceUseCase {

    private static final Logger log = LoggerFactory.getLogger(AttendanceUseCase.class);

    /** タイムゾーン: Asia/Tokyo（打刻時刻から勤務日を算出するために使用） */
    private static final ZoneId ZONE_TOKYO = ZoneId.of("Asia/Tokyo");

    /** 勤怠記録リポジトリ — 集約のCRUD操作 */
    private final AttendanceRecordRepository attendanceRecordRepository;

    /** 勤怠イベントリポジトリ — イベントストアへの追記（INSERT ONLY） */
    private final AttendanceEventRepository attendanceEventRepository;

    /** シフトパターンリポジトリ — シフト制の所定労働時間取得用 */
    private final ShiftPatternRepository shiftPatternRepository;

    /** 勤務時間計算サービス — 固定/シフト/フレックスの3パターン対応 */
    private final WorkDurationCalculator workDurationCalculator;

    /** Springイベント発行 — プロジェクターがRead Modelを更新するトリガー */
    private final ApplicationEventPublisher eventPublisher;

    /** JSONシリアライザ — イベントストアのpayload変換用 */
    private final ObjectMapper objectMapper;

    /**
     * コンストラクタ — 6つの依存を注入する
     */
    public AttendanceUseCase(
            AttendanceRecordRepository attendanceRecordRepository,
            AttendanceEventRepository attendanceEventRepository,
            ShiftPatternRepository shiftPatternRepository,
            WorkDurationCalculator workDurationCalculator,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceEventRepository = attendanceEventRepository;
        this.shiftPatternRepository = shiftPatternRepository;
        this.workDurationCalculator = workDurationCalculator;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    // ========================================
    // UC-ATT-001: 出勤打刻する
    // ========================================

    /**
     * 出勤打刻を実行する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>打刻時刻からAsia/Tokyoタイムゾーンで勤務日を算出する</li>
     *   <li>従業員ID+勤務日で既存レコードを検索し、なければ新規作成する</li>
     *   <li>集約のclockIn()を呼び出す（NOT_CLOCKED→CLOCKED_IN）</li>
     *   <li>集約・打刻エントリ・イベントを保存し、イベントを発行する</li>
     * </ol>
     * </p>
     *
     * @param employeeId     従業員ID
     * @param clockTime      出勤打刻時刻
     * @param source         打刻元（WEB/MOBILE）
     * @param shiftPatternId シフトパターンID（nullable: 固定時間制の場合はnull）
     * @return 作成または更新された勤怠記録ID
     */
    public AttendanceRecordId clockIn(EmployeeId employeeId, ClockTime clockTime,
                                       ClockSource source, ShiftPatternId shiftPatternId) {
        // 打刻時刻からAsia/Tokyoタイムゾーンで勤務日を算出する
        WorkDate workDate = deriveWorkDate(clockTime);
        log.debug("出勤打刻: employeeId={}, workDate={}", employeeId.value(), workDate.value());

        // 既存の勤怠記録を検索し、なければ新規作成する
        AttendanceRecord record = attendanceRecordRepository
                .findByEmployeeIdAndWorkDate(employeeId, workDate)
                .orElseGet(() -> AttendanceRecord.create(employeeId, workDate, shiftPatternId));

        // 集約の出勤打刻コマンドを実行する（NOT_CLOCKED→CLOCKED_IN）
        record.clockIn(clockTime, source);

        // 集約をリポジトリに保存する（新規打刻エントリはsave内で自動保存される）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        publishAndPersistEvents(saved);

        log.debug("出勤打刻完了: attendanceId={}", saved.getId().value());
        return saved.getId();
    }

    // ========================================
    // UC-ATT-002: 退勤打刻する
    // ========================================

    /**
     * 退勤打刻を実行する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>勤怠記録を取得する</li>
     *   <li>集約のclockOut()を呼び出す（CLOCKED_IN→CLOCKED_OUT）</li>
     *   <li>勤務時間を計算する（固定制 or シフト制）</li>
     *   <li>集約・打刻エントリ・イベントを保存し、2つのイベントを発行する</li>
     * </ol>
     * </p>
     *
     * @param id        勤怠記録ID
     * @param clockTime 退勤打刻時刻
     * @param source    打刻元（WEB/MOBILE）
     */
    public WorkDurationCalculator.CalculationResult clockOut(AttendanceRecordId id, ClockTime clockTime, ClockSource source) {
        // 勤怠記録を取得する
        AttendanceRecord record = findRecordOrThrow(id);
        log.debug("退勤打刻: attendanceId={}", id.value());

        // 集約の退勤打刻コマンドを実行する（CLOCKED_IN→CLOCKED_OUT）
        record.clockOut(clockTime, source);

        // 勤務時間を計算する（シフトパターンの有無で計算方法を分岐）
        WorkDurationCalculator.CalculationResult calcResult = calculateWorkDuration(record);

        // 計算結果を集約に設定する
        record.calculateWorkDuration(calcResult.workDuration(), calcResult.overtimeDuration());

        // 集約をリポジトリに保存する（新規打刻エントリはsave内で自動保存される）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        publishAndPersistEvents(saved);

        log.debug("退勤打刻完了: attendanceId={}, netWorkMinutes={}",
                saved.getId().value(), calcResult.workDuration().netWorkMinutes());

        // 計算結果をコントローラに返す（レスポンスDTO組み立て用）
        return calcResult;
    }

    // ========================================
    // UC-ATT-003: 休憩開始する
    // ========================================

    /**
     * 休憩開始を実行する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>勤怠記録を取得する</li>
     *   <li>集約のstartBreak()を呼び出す（ステータスはCLOCKED_INのまま）</li>
     *   <li>集約・打刻エントリ・イベントを保存し、イベントを発行する</li>
     * </ol>
     * </p>
     *
     * @param id        勤怠記録ID
     * @param clockTime 休憩開始時刻
     * @param source    打刻元（WEB/MOBILE）
     */
    public void startBreak(AttendanceRecordId id, ClockTime clockTime, ClockSource source) {
        // 勤怠記録を取得する
        AttendanceRecord record = findRecordOrThrow(id);
        log.debug("休憩開始: attendanceId={}", id.value());

        // 集約の休憩開始コマンドを実行する（ステータス変化なし）
        record.startBreak(clockTime, source);

        // 集約をリポジトリに保存する（新規打刻エントリはsave内で自動保存される）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        publishAndPersistEvents(saved);

        log.debug("休憩開始完了: attendanceId={}", saved.getId().value());
    }

    // ========================================
    // UC-ATT-004: 休憩終了する
    // ========================================

    /**
     * 休憩終了を実行する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>勤怠記録を取得する</li>
     *   <li>集約のendBreak()を呼び出す（ステータスはCLOCKED_INのまま、休憩時間を集約内で計算）</li>
     *   <li>集約・打刻エントリを保存し、ドメインイベントを一括で永続化・発行する</li>
     * </ol>
     * </p>
     *
     * @param id        勤怠記録ID
     * @param clockTime 休憩終了時刻
     * @param source    打刻元（WEB/MOBILE）
     */
    public void endBreak(AttendanceRecordId id, ClockTime clockTime, ClockSource source) {
        // 勤怠記録を取得する
        AttendanceRecord record = findRecordOrThrow(id);
        log.debug("休憩終了: attendanceId={}", id.value());

        // 集約の休憩終了コマンドを実行する（ステータス変化なし、休憩時間は集約内で計算）
        record.endBreak(clockTime, source);

        // 集約をリポジトリに保存する（新規打刻エントリはsave内で自動保存される）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        publishAndPersistEvents(saved);

        log.debug("休憩終了完了: attendanceId={}", saved.getId().value());
    }

    // ========================================
    // UC-ATT-005: 打刻を修正する
    // ========================================

    /**
     * 承認済みの打刻修正を適用する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>勤怠記録を取得する</li>
     *   <li>集約のcorrectClock()を呼び出す（source=CORRECTIONの新エントリ追加、修正前時刻は集約内で取得）</li>
     *   <li>退勤済みの場合は勤務時間を再計算する</li>
     *   <li>集約・打刻エントリを保存し、ドメインイベントを一括で永続化・発行する</li>
     * </ol>
     * </p>
     *
     * @param id         勤怠記録ID
     * @param correction 打刻修正内容（承認済み）
     */
    public void correctClock(AttendanceRecordId id, ClockCorrection correction) {
        // 勤怠記録を取得する
        AttendanceRecord record = findRecordOrThrow(id);
        log.debug("打刻修正: attendanceId={}, targetType={}", id.value(), correction.targetType());

        // 集約の打刻修正コマンドを実行する（source=CORRECTIONの新エントリが追加される、修正前時刻は集約内で取得）
        record.correctClock(correction);

        // 退勤済みの場合は勤務時間を再計算する（出勤・退勤の両方が揃っている）
        if (record.getStatus() == AttendanceStatus.CLOCKED_OUT) {
            WorkDurationCalculator.CalculationResult calcResult = calculateWorkDuration(record);
            record.calculateWorkDuration(calcResult.workDuration(), calcResult.overtimeDuration());
        }

        // 集約をリポジトリに保存する（新規打刻エントリはsave内で自動保存される）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        publishAndPersistEvents(saved);

        log.debug("打刻修正完了: attendanceId={}", saved.getId().value());
    }

    // ========================================
    // UC-ATT-006: 勤務実績を登録する
    // ========================================

    /**
     * 承認済みの手動勤務実績を登録する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>既存レコードを検索し、なければ新規作成する</li>
     *   <li>集約のregisterManualAttendance()を呼び出す（NOT_CLOCKED→CLOCKED_OUT）</li>
     *   <li>勤務時間を計算する（固定制 or シフト制）</li>
     *   <li>集約・打刻エントリ・イベントを保存し、2つのイベントを発行する</li>
     * </ol>
     * </p>
     *
     * @param employeeId     従業員ID
     * @param workDate       勤務日
     * @param manual         手動勤務登録内容（承認済み）
     * @param shiftPatternId シフトパターンID（nullable: 固定時間制の場合はnull）
     * @return 作成された勤怠記録ID
     */
    public AttendanceRecordId registerManualAttendance(
            EmployeeId employeeId, WorkDate workDate,
            ManualAttendance manual, ShiftPatternId shiftPatternId) {
        log.debug("手動勤務登録: employeeId={}, workDate={}", employeeId.value(), workDate.value());

        // 既存レコードを検索し、なければ新規作成する
        AttendanceRecord record = attendanceRecordRepository
                .findByEmployeeIdAndWorkDate(employeeId, workDate)
                .orElseGet(() -> AttendanceRecord.create(employeeId, workDate, shiftPatternId));

        // 集約の手動勤務登録コマンドを実行する（出勤+退勤を一括登録、NOT_CLOCKED→CLOCKED_OUT）
        record.registerManualAttendance(manual);

        // 勤務時間を計算する（シフトパターンの有無で計算方法を分岐）
        WorkDurationCalculator.CalculationResult calcResult = calculateWorkDuration(record);

        // 計算結果を集約に設定する
        record.calculateWorkDuration(calcResult.workDuration(), calcResult.overtimeDuration());

        // 集約をリポジトリに保存する（新規打刻エントリはsave内で自動保存される）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        publishAndPersistEvents(saved);

        log.debug("手動勤務登録完了: attendanceId={}, netWorkMinutes={}",
                saved.getId().value(), calcResult.workDuration().netWorkMinutes());
        return saved.getId();
    }

    // ========================================
    // UC-ATT-007: 本締め確定する
    // ========================================

    /**
     * 月次本締めにより勤怠記録を確定する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>勤怠記録を取得する</li>
     *   <li>集約のfinalizeRecord()を呼び出す（CLOCKED_OUT→FINALIZED）</li>
     *   <li>集約・イベントを保存し、イベントを発行する</li>
     * </ol>
     * ※ 打刻エントリの追加はなし（ステータス変更のみ）
     * </p>
     *
     * @param id               勤怠記録ID
     * @param monthlyClosingId 月次締めID（どの締め処理に基づく確定か）
     */
    public void finalizeRecord(AttendanceRecordId id, MonthlyClosingId monthlyClosingId) {
        // 勤怠記録を取得する
        AttendanceRecord record = findRecordOrThrow(id);
        log.debug("本締め確定: attendanceId={}, monthlyClosingId={}", id.value(), monthlyClosingId);

        // 集約の本締め確定コマンドを実行する（CLOCKED_OUT→FINALIZED）
        record.finalizeRecord(monthlyClosingId);

        // 集約をリポジトリに保存する（打刻エントリの追加はなし）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        publishAndPersistEvents(saved);

        log.debug("本締め確定完了: attendanceId={}", saved.getId().value());
    }

    // ========================================
    // ヘルパーメソッド — 共通処理
    // ========================================

    /**
     * 勤怠記録をIDで取得する（見つからない場合は例外をスロー）
     *
     * @param id 勤怠記録ID
     * @return 勤怠記録
     * @throws IllegalArgumentException 勤怠記録が見つからない場合
     */
    private AttendanceRecord findRecordOrThrow(AttendanceRecordId id) {
        return attendanceRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "勤怠記録が見つかりません: " + id.value()));
    }

    /**
     * 打刻時刻からAsia/Tokyoタイムゾーンで勤務日を算出する
     *
     * <p>Instant（UTC瞬間値）をAsia/Tokyoのローカル日付に変換し、
     * WorkDate値オブジェクトとして返す。</p>
     *
     * @param clockTime 打刻時刻
     * @return 勤務日
     */
    private WorkDate deriveWorkDate(ClockTime clockTime) {
        return new WorkDate(clockTime.value().atZone(ZONE_TOKYO).toLocalDate());
    }

    /**
     * 勤務時間を計算する（シフトパターンの有無で計算方法を分岐）
     *
     * <p>シフトパターンIDがある場合: ShiftPatternから所定労働時間を取得してシフト制で計算
     * <br>シフトパターンIDがない場合: 固定時間制（所定480分）で計算</p>
     *
     * @param record 勤怠記録（退勤打刻済みであること）
     * @return 勤務時間+残業時間の計算結果
     */
    private WorkDurationCalculator.CalculationResult calculateWorkDuration(AttendanceRecord record) {
        if (record.getShiftPatternId() != null) {
            // シフト制: シフトパターンから所定労働時間を取得して計算する
            ShiftPattern pattern = shiftPatternRepository.findById(record.getShiftPatternId())
                    .orElseThrow(() -> new IllegalStateException(
                            "シフトパターンが見つかりません: " + record.getShiftPatternId().value()));
            int scheduledMinutes = pattern.calculateScheduledMinutes();
            return workDurationCalculator.calculateForShift(record, scheduledMinutes);
        } else {
            // 固定時間制: 所定労働時間480分（8時間）で計算する
            return workDurationCalculator.calculateForFixed(record);
        }
    }

    /**
     * 集約に蓄積されたドメインイベントを一括で永続化・発行する
     *
     * <p>集約のコマンドメソッドが registerEvent() で登録したイベントを
     * getDomainEvents() で取得し、イベントストアへの記録と Spring イベント発行を行う。
     * 処理完了後に clearDomainEvents() でイベントリストをクリアする。</p>
     *
     * @param record 保存済みの勤怠記録（ドメインイベントが蓄積されている）
     */
    private void publishAndPersistEvents(AttendanceRecord record) {
        for (DomainEvent event : record.getDomainEvents()) {
            // イベントストアに記録する
            persistEvent(record.getId(), event);
            // Springイベントとして発行する（プロジェクターがRead Modelを更新）
            eventPublisher.publishEvent(event);
        }
        // イベントリストをクリアして二重永続化を防止する
        record.clearDomainEvents();
    }

    /**
     * ドメインイベントをイベントストアに保存する
     *
     * <p>イベントオブジェクトをJacksonでJSON文字列に変換し、
     * attendance_eventsテーブルにINSERTする。
     * eventType・occurredAt はイベント自身から取得する（型安全）。</p>
     *
     * @param attendanceId 勤怠記録ID
     * @param event        ドメインイベント（DomainEvent基底クラス）
     */
    private void persistEvent(AttendanceRecordId attendanceId, DomainEvent event) {
        try {
            // イベントオブジェクトをJSON文字列に変換する
            String payloadJson = objectMapper.writeValueAsString(event);
            // イベントストアに追記する（INSERT ONLY）
            attendanceEventRepository.append(event.getEventId(), attendanceId, event.getEventType(), payloadJson, event.getOccurredAt());
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "イベントのJSON変換に失敗しました: eventType=" + event.getEventType(), e);
        }
    }
}
