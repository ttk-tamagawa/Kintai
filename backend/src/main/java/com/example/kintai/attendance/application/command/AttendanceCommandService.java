package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.*;
import com.example.kintai.attendance.domain.model.event.*;
import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.repository.AttendanceEventRepository;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.attendance.domain.repository.ClockEntryRepository;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.domain.service.WorkDurationCalculator;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 勤怠記録コマンドサービス — 勤怠記録の7つの書き込みユースケースを統合するアプリケーションサービス
 *
 * <p>各コマンドメソッドは以下の共通フローで処理する:
 * <ol>
 *   <li>集約（AttendanceRecord）をリポジトリから取得する（または新規作成する）</li>
 *   <li>集約のコマンドメソッドを呼び出す（ドメインロジック実行）</li>
 *   <li>必要に応じて勤務時間を計算する（ドメインサービス）</li>
 *   <li>集約をリポジトリに保存する</li>
 *   <li>新規打刻エントリを打刻テーブルに追記する</li>
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
public class AttendanceCommandService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceCommandService.class);

    /** タイムゾーン: Asia/Tokyo（打刻時刻から勤務日を算出するために使用） */
    private static final ZoneId ZONE_TOKYO = ZoneId.of("Asia/Tokyo");

    /** 勤怠記録リポジトリ — 集約のCRUD操作 */
    private final AttendanceRecordRepository attendanceRecordRepository;

    /** 勤怠イベントリポジトリ — イベントストアへの追記（INSERT ONLY） */
    private final AttendanceEventRepository attendanceEventRepository;

    /** 打刻エントリリポジトリ — 打刻ログの追記（INSERT ONLY） */
    private final ClockEntryRepository clockEntryRepository;

    /** シフトパターンリポジトリ — シフト制の所定労働時間取得用 */
    private final ShiftPatternRepository shiftPatternRepository;

    /** 勤務時間計算サービス — 固定/シフト/フレックスの3パターン対応 */
    private final WorkDurationCalculator workDurationCalculator;

    /** Springイベント発行 — プロジェクターがRead Modelを更新するトリガー */
    private final ApplicationEventPublisher eventPublisher;

    /** JSONシリアライザ — イベントストアのpayload変換用 */
    private final ObjectMapper objectMapper;

    /**
     * コンストラクタ — 7つの依存を注入する
     */
    public AttendanceCommandService(
            AttendanceRecordRepository attendanceRecordRepository,
            AttendanceEventRepository attendanceEventRepository,
            ClockEntryRepository clockEntryRepository,
            ShiftPatternRepository shiftPatternRepository,
            WorkDurationCalculator workDurationCalculator,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceEventRepository = attendanceEventRepository;
        this.clockEntryRepository = clockEntryRepository;
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

        // コマンド実行前の打刻エントリ数を記録する（新規エントリ抽出用）
        int entryCountBefore = record.getClockEntries().size();

        // 集約の出勤打刻コマンドを実行する（NOT_CLOCKED→CLOCKED_IN）
        record.clockIn(clockTime, source);

        // 新規打刻エントリを保存前に抽出する
        List<ClockEntry> newEntries = extractNewEntries(record.getClockEntries(), entryCountBefore);

        // 集約をリポジトリに保存する
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 新規打刻エントリを打刻テーブルに追記する
        if (!newEntries.isEmpty()) {
            clockEntryRepository.appendAll(saved.getId(), newEntries);
        }

        // ドメインイベントを生成する
        ClockedInEvent event = ClockedInEvent.of(
                saved.getId(), employeeId, workDate, clockTime, source
        );

        // イベントストアに記録する
        persistEvent(saved.getId(), "CLOCKED_IN", event, event.occurredAt());

        // イベントを発行する（プロジェクターがattendance_summariesを更新）
        eventPublisher.publishEvent(event);

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
    public void clockOut(AttendanceRecordId id, ClockTime clockTime, ClockSource source) {
        // 勤怠記録を取得する
        AttendanceRecord record = findRecordOrThrow(id);
        log.debug("退勤打刻: attendanceId={}", id.value());

        // コマンド実行前の打刻エントリ数を記録する
        int entryCountBefore = record.getClockEntries().size();

        // 集約の退勤打刻コマンドを実行する（CLOCKED_IN→CLOCKED_OUT）
        record.clockOut(clockTime, source);

        // 勤務時間を計算する（シフトパターンの有無で計算方法を分岐）
        WorkDurationCalculator.CalculationResult calcResult = calculateWorkDuration(record);

        // 計算結果を集約に設定する
        record.calculateWorkDuration(calcResult.workDuration(), calcResult.overtimeDuration());

        // 新規打刻エントリを保存前に抽出する
        List<ClockEntry> newEntries = extractNewEntries(record.getClockEntries(), entryCountBefore);

        // 集約をリポジトリに保存する
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 新規打刻エントリを打刻テーブルに追記する
        if (!newEntries.isEmpty()) {
            clockEntryRepository.appendAll(saved.getId(), newEntries);
        }

        // ClockedOutイベントを生成・保存・発行する
        ClockedOutEvent clockedOutEvent = ClockedOutEvent.of(
                saved.getId(), record.getEmployeeId(), clockTime, source
        );
        persistEvent(saved.getId(), "CLOCKED_OUT", clockedOutEvent, clockedOutEvent.occurredAt());
        eventPublisher.publishEvent(clockedOutEvent);

        // WorkDurationCalculatedイベントを生成・保存・発行する
        WorkDurationCalculatedEvent calcEvent = WorkDurationCalculatedEvent.of(
                saved.getId(), record.getEmployeeId(),
                calcResult.workDuration(), calcResult.overtimeDuration()
        );
        persistEvent(saved.getId(), "WORK_DURATION_CALCULATED", calcEvent, calcEvent.occurredAt());
        eventPublisher.publishEvent(calcEvent);

        log.debug("退勤打刻完了: attendanceId={}, netWorkMinutes={}",
                saved.getId().value(), calcResult.workDuration().netWorkMinutes());
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

        // コマンド実行前の打刻エントリ数を記録する
        int entryCountBefore = record.getClockEntries().size();

        // 集約の休憩開始コマンドを実行する（ステータス変化なし）
        record.startBreak(clockTime, source);

        // 新規打刻エントリを保存前に抽出する
        List<ClockEntry> newEntries = extractNewEntries(record.getClockEntries(), entryCountBefore);

        // 集約をリポジトリに保存する
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 新規打刻エントリを打刻テーブルに追記する
        if (!newEntries.isEmpty()) {
            clockEntryRepository.appendAll(saved.getId(), newEntries);
        }

        // BreakStartedイベントを生成・保存・発行する
        BreakStartedEvent event = BreakStartedEvent.of(
                saved.getId(), record.getEmployeeId(), clockTime
        );
        persistEvent(saved.getId(), "BREAK_STARTED", event, event.occurredAt());
        eventPublisher.publishEvent(event);

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
     *   <li>最後のBREAK_START時刻を取得する（休憩時間計算用）</li>
     *   <li>集約のendBreak()を呼び出す（ステータスはCLOCKED_INのまま）</li>
     *   <li>今回の休憩時間（分）を算出する</li>
     *   <li>集約・打刻エントリ・イベントを保存し、イベントを発行する</li>
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

        // 最後のBREAK_START時刻を取得する（休憩時間計算のため、コマンド実行前に取得）
        ClockTime lastBreakStart = findLatestTimeOfType(record.getClockEntries(), ClockType.BREAK_START);

        // コマンド実行前の打刻エントリ数を記録する
        int entryCountBefore = record.getClockEntries().size();

        // 集約の休憩終了コマンドを実行する（ステータス変化なし）
        record.endBreak(clockTime, source);

        // 今回の休憩時間（分）を算出する（BREAK_START → BREAK_END の差分）
        int breakMinutes = (int) Duration.between(lastBreakStart.value(), clockTime.value()).toMinutes();

        // 新規打刻エントリを保存前に抽出する
        List<ClockEntry> newEntries = extractNewEntries(record.getClockEntries(), entryCountBefore);

        // 集約をリポジトリに保存する
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 新規打刻エントリを打刻テーブルに追記する
        if (!newEntries.isEmpty()) {
            clockEntryRepository.appendAll(saved.getId(), newEntries);
        }

        // BreakEndedイベントを生成・保存・発行する（breakMinutesを含む）
        BreakEndedEvent event = BreakEndedEvent.of(
                saved.getId(), record.getEmployeeId(), clockTime, breakMinutes
        );
        persistEvent(saved.getId(), "BREAK_ENDED", event, event.occurredAt());
        eventPublisher.publishEvent(event);

        log.debug("休憩終了完了: attendanceId={}, breakMinutes={}", saved.getId().value(), breakMinutes);
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
     *   <li>修正前の打刻時刻を取得する（イベント記録用、修正実行前に取得）</li>
     *   <li>集約のcorrectClock()を呼び出す（source=CORRECTIONの新エントリ追加）</li>
     *   <li>退勤済みの場合は勤務時間を再計算する</li>
     *   <li>集約・打刻エントリ・イベントを保存し、イベントを発行する</li>
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

        // 修正前の打刻時刻を取得する（イベント記録用。修正実行前に取得する必要がある）
        ClockTime beforeTime = findLatestTimeOfType(record.getClockEntries(), correction.targetType());

        // コマンド実行前の打刻エントリ数を記録する
        int entryCountBefore = record.getClockEntries().size();

        // 集約の打刻修正コマンドを実行する（source=CORRECTIONの新エントリが追加される）
        record.correctClock(correction);

        // 退勤済みの場合は勤務時間を再計算する（出勤・退勤の両方が揃っている）
        if (record.getStatus() == AttendanceStatus.CLOCKED_OUT) {
            WorkDurationCalculator.CalculationResult calcResult = calculateWorkDuration(record);
            record.calculateWorkDuration(calcResult.workDuration(), calcResult.overtimeDuration());
        }

        // 新規打刻エントリを保存前に抽出する
        List<ClockEntry> newEntries = extractNewEntries(record.getClockEntries(), entryCountBefore);

        // 集約をリポジトリに保存する
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 新規打刻エントリを打刻テーブルに追記する
        if (!newEntries.isEmpty()) {
            clockEntryRepository.appendAll(saved.getId(), newEntries);
        }

        // ClockCorrectedイベントを生成・保存・発行する
        ClockCorrectedEvent correctedEvent = ClockCorrectedEvent.of(
                saved.getId(), record.getEmployeeId(),
                correction.targetType(), beforeTime, correction.correctedTime(),
                correction.approvalId()
        );
        persistEvent(saved.getId(), "CLOCK_CORRECTED", correctedEvent, correctedEvent.occurredAt());
        eventPublisher.publishEvent(correctedEvent);

        // 退勤済みの場合は再計算イベントも発行する
        if (record.getStatus() == AttendanceStatus.CLOCKED_OUT) {
            WorkDurationCalculatedEvent calcEvent = WorkDurationCalculatedEvent.of(
                    saved.getId(), record.getEmployeeId(),
                    record.getWorkDuration(), record.getOvertimeDuration()
            );
            persistEvent(saved.getId(), "WORK_DURATION_CALCULATED", calcEvent, calcEvent.occurredAt());
            eventPublisher.publishEvent(calcEvent);
        }

        log.debug("打刻修正完了: attendanceId={}, {} → {}",
                saved.getId().value(), beforeTime.value(), correction.correctedTime().value());
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

        // コマンド実行前の打刻エントリ数を記録する
        int entryCountBefore = record.getClockEntries().size();

        // 集約の手動勤務登録コマンドを実行する（出勤+退勤を一括登録、NOT_CLOCKED→CLOCKED_OUT）
        record.registerManualAttendance(manual);

        // 勤務時間を計算する（シフトパターンの有無で計算方法を分岐）
        WorkDurationCalculator.CalculationResult calcResult = calculateWorkDuration(record);

        // 計算結果を集約に設定する
        record.calculateWorkDuration(calcResult.workDuration(), calcResult.overtimeDuration());

        // 新規打刻エントリを保存前に抽出する
        List<ClockEntry> newEntries = extractNewEntries(record.getClockEntries(), entryCountBefore);

        // 集約をリポジトリに保存する
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // 新規打刻エントリを打刻テーブルに追記する（出勤+退勤の2エントリ）
        if (!newEntries.isEmpty()) {
            clockEntryRepository.appendAll(saved.getId(), newEntries);
        }

        // ManualAttendanceRegisteredイベントを生成・保存・発行する
        ManualAttendanceRegisteredEvent manualEvent = ManualAttendanceRegisteredEvent.of(
                saved.getId(), employeeId, workDate,
                manual.startTime(), manual.endTime(), manual.type(), manual.approvalId()
        );
        persistEvent(saved.getId(), "MANUAL_ATTENDANCE_REGISTERED", manualEvent, manualEvent.occurredAt());
        eventPublisher.publishEvent(manualEvent);

        // WorkDurationCalculatedイベントを生成・保存・発行する
        WorkDurationCalculatedEvent calcEvent = WorkDurationCalculatedEvent.of(
                saved.getId(), employeeId,
                calcResult.workDuration(), calcResult.overtimeDuration()
        );
        persistEvent(saved.getId(), "WORK_DURATION_CALCULATED", calcEvent, calcEvent.occurredAt());
        eventPublisher.publishEvent(calcEvent);

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
    public void finalizeRecord(AttendanceRecordId id, String monthlyClosingId) {
        // 勤怠記録を取得する
        AttendanceRecord record = findRecordOrThrow(id);
        log.debug("本締め確定: attendanceId={}, monthlyClosingId={}", id.value(), monthlyClosingId);

        // 集約の本締め確定コマンドを実行する（CLOCKED_OUT→FINALIZED）
        record.finalizeRecord(monthlyClosingId);

        // 集約をリポジトリに保存する（打刻エントリの追加はなし）
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        // AttendanceFinalizedイベントを生成・保存・発行する
        AttendanceFinalizedEvent event = AttendanceFinalizedEvent.of(
                saved.getId(), record.getEmployeeId(), record.getWorkDate(), monthlyClosingId
        );
        persistEvent(saved.getId(), "ATTENDANCE_FINALIZED", event, event.occurredAt());
        eventPublisher.publishEvent(event);

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
     * 打刻エントリ一覧から新規追加分を抽出する
     *
     * <p>コマンド実行前のエントリ数（countBefore）以降のエントリが新規追加分。
     * 保存前に抽出することで、リポジトリ実装に依存しない安全な取得を保証する。</p>
     *
     * @param allEntries  全打刻エントリ（コマンド実行後）
     * @param countBefore コマンド実行前のエントリ数
     * @return 新規追加されたエントリのリスト
     */
    private List<ClockEntry> extractNewEntries(List<ClockEntry> allEntries, int countBefore) {
        if (countBefore >= allEntries.size()) {
            return List.of();
        }
        return new ArrayList<>(allEntries.subList(countBefore, allEntries.size()));
    }

    /**
     * 指定種別の最新の打刻時刻を取得する（末尾から検索）
     *
     * <p>打刻修正（CORRECTION）がある場合は最後のエントリが有効な時刻となる。
     * 修正前の時刻取得やBREAK_START時刻取得に使用する。</p>
     *
     * @param entries 打刻エントリ一覧
     * @param type    取得する打刻種別
     * @return 最新の打刻時刻
     * @throws IllegalStateException 指定種別の打刻が見つからない場合
     */
    private ClockTime findLatestTimeOfType(List<ClockEntry> entries, ClockType type) {
        // 末尾から検索して最新のエントリを取得する
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).type() == type) {
                return entries.get(i).time();
            }
        }
        throw new IllegalStateException(type + "の打刻が見つかりません");
    }

    /**
     * ドメインイベントをイベントストアに保存する
     *
     * <p>イベントオブジェクトをJacksonでJSON文字列に変換し、
     * attendance_eventsテーブルにINSERTする。</p>
     *
     * @param attendanceId 勤怠記録ID
     * @param eventType    イベント種別（CLOCKED_IN, CLOCKED_OUT 等）
     * @param event        ドメインイベントオブジェクト
     * @param occurredAt   イベント発生日時
     */
    private void persistEvent(AttendanceRecordId attendanceId, String eventType,
                               Object event, Instant occurredAt) {
        try {
            // イベントオブジェクトをJSON文字列に変換する
            String payloadJson = objectMapper.writeValueAsString(event);
            // イベントストアに追記する（INSERT ONLY）
            attendanceEventRepository.append(attendanceId, eventType, payloadJson, occurredAt);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "イベントのJSON変換に失敗しました: eventType=" + eventType, e);
        }
    }
}
