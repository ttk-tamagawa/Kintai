package com.example.kintai.attendance.presentation.controller;

import com.example.kintai.attendance.application.command.AttendanceCommandService;
import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.model.ClockEntry;
import com.example.kintai.attendance.domain.model.ClockSource;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.attendance.domain.model.ClockType;
import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.attendance.domain.service.WorkDurationCalculator;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.attendance.presentation.dto.BreakEndResponse;
import com.example.kintai.attendance.presentation.dto.BreakStartResponse;
import com.example.kintai.attendance.presentation.dto.ClockActionRequest;
import com.example.kintai.attendance.presentation.dto.ClockInResponse;
import com.example.kintai.attendance.presentation.dto.ClockOutResponse;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 勤怠記録コマンドコントローラー — 4つの打刻APIエンドポイントを提供する
 *
 * <p>対応エンドポイント:
 * <ul>
 *   <li>POST /api/v1/attendances/clock-in — 出勤打刻</li>
 *   <li>POST /api/v1/attendances/clock-out — 退勤打刻</li>
 *   <li>POST /api/v1/attendances/break-start — 休憩開始</li>
 *   <li>POST /api/v1/attendances/break-end — 休憩終了</li>
 * </ul>
 * </p>
 *
 * <p>各エンドポイントの処理フロー:
 * <ol>
 *   <li>リクエストDTOをドメインオブジェクトに変換する</li>
 *   <li>退勤・休憩系は従業員ID+勤務日で既存レコードを検索する</li>
 *   <li>AttendanceCommandServiceのコマンドメソッドを呼び出す</li>
 *   <li>保存後のレコードを再取得してレスポンスDTOを組み立てる</li>
 * </ol>
 * </p>
 *
 * <p>認可: 全メソッド EMPLOYEE ロール必須</p>
 */
@RestController
@RequestMapping("/api/v1/attendances")
public class AttendanceCommandController {

    private static final Logger log = LoggerFactory.getLogger(AttendanceCommandController.class);

    /** タイムゾーン: Asia/Tokyo（打刻時刻から勤務日を算出するために使用） */
    private static final ZoneId ZONE_TOKYO = ZoneId.of("Asia/Tokyo");

    /** 勤怠記録コマンドサービス — ドメインロジックの実行を委譲する */
    private final AttendanceCommandService commandService;

    /** 勤怠記録リポジトリ — 従業員ID+勤務日でのレコード検索・レスポンス組み立て用 */
    private final AttendanceRecordRepository attendanceRecordRepository;

    public AttendanceCommandController(
            AttendanceCommandService commandService,
            AttendanceRecordRepository attendanceRecordRepository) {
        this.commandService = commandService;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    // ========================================
    // POST /clock-in — 出勤打刻
    // ========================================

    /**
     * 出勤打刻を実行する
     *
     * <p>従業員の出勤を記録する。当日の勤怠記録がなければ新規作成し、
     * ステータスをNOT_CLOCKED→CLOCKED_INに遷移させる。</p>
     *
     * @param request 打刻リクエスト（employeeId, clockTime, source, shiftPatternId）
     * @return 出勤打刻結果（勤怠記録ID、ステータス、出勤時刻等）
     */
    @PostMapping("/clock-in")
    @PreAuthorize("hasRole('EMPLOYEE') and @accessControl.canAccessEmployee(authentication, #request.employeeId())")
    public ResponseEntity<ClockInResponse> clockIn(@Valid @RequestBody ClockActionRequest request) {
        log.debug("出勤打刻リクエスト受信: employeeId={}", request.employeeId());

        // リクエストをドメインオブジェクトに変換する
        EmployeeId employeeId = EmployeeId.of(request.employeeId());
        ClockTime clockTime = new ClockTime(request.clockTime().toInstant());
        ClockSource source = parseClockSource(request.source());
        ShiftPatternId shiftPatternId = request.shiftPatternId() != null
                ? ShiftPatternId.of(request.shiftPatternId()) : null;

        // アプリケーションサービスを呼び出す（新規作成 or 既存取得 → clockIn → 保存）
        AttendanceRecordId attendanceId = commandService.clockIn(
                employeeId, clockTime, source, shiftPatternId);

        // 保存後のレコードを取得してレスポンスを組み立てる
        AttendanceRecord record = findRecordOrThrow(attendanceId);

        // 出勤時刻を打刻エントリから取得する
        Instant clockInTime = findLatestTimeOfType(record.getClockEntries(), ClockType.CLOCK_IN);

        // レスポンスDTO組み立て（EmployeeId: String→UUID変換）
        ClockInResponse response = new ClockInResponse(
                record.getId().value(),
                record.getEmployeeId().value(),
                record.getWorkDate().value(),
                record.getStatus().name(),
                clockInTime,
                source.name(),
                record.getUpdatedAt()
        );

        log.debug("出勤打刻完了: attendanceId={}", attendanceId.value());
        return ResponseEntity.ok(response);
    }

    // ========================================
    // POST /clock-out — 退勤打刻
    // ========================================

    /**
     * 退勤打刻を実行する
     *
     * <p>従業員の退勤を記録する。CLOCKED_IN→CLOCKED_OUTに遷移させ、
     * 勤務時間・残業時間を自動計算する。</p>
     *
     * @param request 打刻リクエスト（employeeId, clockTime, source）
     * @return 退勤打刻結果（勤務時間、残業時間等を含む）
     */
    @PostMapping("/clock-out")
    @PreAuthorize("hasRole('EMPLOYEE') and @accessControl.canAccessEmployee(authentication, #request.employeeId())")
    public ResponseEntity<ClockOutResponse> clockOut(@Valid @RequestBody ClockActionRequest request) {
        log.debug("退勤打刻リクエスト受信: employeeId={}", request.employeeId());

        // リクエストをドメインオブジェクトに変換する
        EmployeeId employeeId = EmployeeId.of(request.employeeId());
        ClockTime clockTime = new ClockTime(request.clockTime().toInstant());
        ClockSource source = parseClockSource(request.source());

        // 従業員ID+勤務日で当日の勤怠記録を検索する
        WorkDate workDate = deriveWorkDate(clockTime);
        AttendanceRecord record = findRecordByEmployeeAndDate(employeeId, workDate);

        // アプリケーションサービスを呼び出す（clockOut → 勤務時間計算 → 保存）
        // 計算結果を受け取る（リポジトリはWorkDurationを永続化しないためサービスから取得）
        WorkDurationCalculator.CalculationResult calcResult =
                commandService.clockOut(record.getId(), clockTime, source);

        // 保存後のレコードを再取得してレスポンスを組み立てる
        AttendanceRecord updated = findRecordOrThrow(record.getId());

        // 出勤・退勤時刻を打刻エントリから取得する
        Instant clockInTime = findLatestTimeOfType(updated.getClockEntries(), ClockType.CLOCK_IN);
        Instant clockOutTime = findLatestTimeOfType(updated.getClockEntries(), ClockType.CLOCK_OUT);

        // レスポンスDTO組み立て（EmployeeId: String→UUID変換）
        ClockOutResponse response = new ClockOutResponse(
                updated.getId().value(),
                updated.getEmployeeId().value(),
                updated.getWorkDate().value(),
                updated.getStatus().name(),
                clockInTime,
                clockOutTime,
                calcResult.workDuration().breakMinutes(),
                calcResult.workDuration().netWorkMinutes(),
                calcResult.overtimeDuration().totalOvertimeMinutes(),
                source.name(),
                updated.getUpdatedAt()
        );

        log.debug("退勤打刻完了: attendanceId={}", record.getId().value());
        return ResponseEntity.ok(response);
    }

    // ========================================
    // POST /break-start — 休憩開始
    // ========================================

    /**
     * 休憩開始を記録する
     *
     * <p>出勤中の従業員の休憩開始を記録する。
     * ステータスはCLOCKED_INのまま、onBreakフラグがtrueになる。</p>
     *
     * @param request 打刻リクエスト（employeeId, clockTime, source）
     * @return 休憩開始結果（onBreak=true、休憩開始時刻）
     */
    @PostMapping("/break-start")
    @PreAuthorize("hasRole('EMPLOYEE') and @accessControl.canAccessEmployee(authentication, #request.employeeId())")
    public ResponseEntity<BreakStartResponse> breakStart(@Valid @RequestBody ClockActionRequest request) {
        log.debug("休憩開始リクエスト受信: employeeId={}", request.employeeId());

        // リクエストをドメインオブジェクトに変換する
        EmployeeId employeeId = EmployeeId.of(request.employeeId());
        ClockTime clockTime = new ClockTime(request.clockTime().toInstant());
        ClockSource source = parseClockSource(request.source());

        // 従業員ID+勤務日で当日の勤怠記録を検索する
        WorkDate workDate = deriveWorkDate(clockTime);
        AttendanceRecord record = findRecordByEmployeeAndDate(employeeId, workDate);

        // アプリケーションサービスを呼び出す（startBreak → 保存）
        commandService.startBreak(record.getId(), clockTime, source);

        // 保存後のレコードを再取得してレスポンスを組み立てる
        AttendanceRecord updated = findRecordOrThrow(record.getId());

        // レスポンスDTO組み立て（EmployeeId: String→UUID変換）
        BreakStartResponse response = new BreakStartResponse(
                updated.getId().value(),
                updated.getEmployeeId().value(),
                updated.getWorkDate().value(),
                updated.getStatus().name(),
                true,  // 休憩開始直後なので必ずtrue
                clockTime.value(),  // 今回の休憩開始時刻
                updated.getUpdatedAt()
        );

        log.debug("休憩開始完了: attendanceId={}", record.getId().value());
        return ResponseEntity.ok(response);
    }

    // ========================================
    // POST /break-end — 休憩終了
    // ========================================

    /**
     * 休憩終了を記録する
     *
     * <p>休憩中の従業員の休憩終了を記録する。
     * onBreakフラグがfalseになり、累計休憩時間を返却する。</p>
     *
     * @param request 打刻リクエスト（employeeId, clockTime, source）
     * @return 休憩終了結果（onBreak=false、合計休憩時間）
     */
    @PostMapping("/break-end")
    @PreAuthorize("hasRole('EMPLOYEE') and @accessControl.canAccessEmployee(authentication, #request.employeeId())")
    public ResponseEntity<BreakEndResponse> breakEnd(@Valid @RequestBody ClockActionRequest request) {
        log.debug("休憩終了リクエスト受信: employeeId={}", request.employeeId());

        // リクエストをドメインオブジェクトに変換する
        EmployeeId employeeId = EmployeeId.of(request.employeeId());
        ClockTime clockTime = new ClockTime(request.clockTime().toInstant());
        ClockSource source = parseClockSource(request.source());

        // 従業員ID+勤務日で当日の勤怠記録を検索する
        WorkDate workDate = deriveWorkDate(clockTime);
        AttendanceRecord record = findRecordByEmployeeAndDate(employeeId, workDate);

        // アプリケーションサービスを呼び出す（endBreak → 保存）
        commandService.endBreak(record.getId(), clockTime, source);

        // 保存後のレコードを再取得してレスポンスを組み立てる
        AttendanceRecord updated = findRecordOrThrow(record.getId());

        // 合計休憩時間を打刻エントリから計算する
        int totalBreakMinutes = calculateTotalBreakMinutes(updated.getClockEntries());

        // レスポンスDTO組み立て（EmployeeId: String→UUID変換）
        BreakEndResponse response = new BreakEndResponse(
                updated.getId().value(),
                updated.getEmployeeId().value(),
                updated.getWorkDate().value(),
                updated.getStatus().name(),
                false,  // 休憩終了直後なので必ずfalse
                totalBreakMinutes,
                updated.getUpdatedAt()
        );

        log.debug("休憩終了完了: attendanceId={}", record.getId().value());
        return ResponseEntity.ok(response);
    }

    // ========================================
    // ヘルパーメソッド
    // ========================================

    /**
     * 打刻時刻からAsia/Tokyoタイムゾーンで勤務日を算出する
     *
     * @param clockTime 打刻時刻
     * @return 勤務日（Asia/Tokyo基準）
     */
    private WorkDate deriveWorkDate(ClockTime clockTime) {
        return new WorkDate(clockTime.value().atZone(ZONE_TOKYO).toLocalDate());
    }

    /**
     * 従業員ID+勤務日で勤怠記録を検索する（見つからない場合は404エラー）
     *
     * @param employeeId 従業員ID
     * @param workDate   勤務日
     * @return 勤怠記録
     * @throws IllegalArgumentException 勤怠記録が見つからない場合
     */
    private AttendanceRecord findRecordByEmployeeAndDate(EmployeeId employeeId, WorkDate workDate) {
        return attendanceRecordRepository.findByEmployeeIdAndWorkDate(employeeId, workDate)
                .orElseThrow(() -> new IllegalArgumentException(
                        "当日の勤怠記録が見つかりません: employeeId=" + employeeId.value()
                                + ", workDate=" + workDate.value()));
    }

    /**
     * 勤怠記録IDでレコードを取得する（見つからない場合はシステムエラー）
     *
     * @param id 勤怠記録ID
     * @return 勤怠記録
     * @throws IllegalStateException 保存直後のレコードが取得できない場合
     */
    private AttendanceRecord findRecordOrThrow(AttendanceRecordId id) {
        return attendanceRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "保存直後のレコードが取得できません: attendanceId=" + id.value()));
    }

    /**
     * 打刻元文字列をClockSource enumに変換する
     *
     * <p>無効な値が指定された場合は分かりやすいエラーメッセージを返す。</p>
     *
     * @param source 打刻元文字列（"WEB" / "MOBILE"）
     * @return ClockSource enum値
     * @throws IllegalArgumentException 無効な打刻元の場合
     */
    private ClockSource parseClockSource(String source) {
        try {
            return ClockSource.valueOf(source);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "無効な打刻元です: " + source + "（WEB または MOBILE を指定してください）");
        }
    }

    /**
     * 指定種別の最新打刻時刻を取得する
     *
     * <p>打刻エントリ一覧を末尾から検索し、最初に見つかった指定種別の時刻を返す。</p>
     *
     * @param entries 打刻エントリ一覧
     * @param type    検索する打刻種別
     * @return 最新の打刻時刻（該当なしの場合はnull）
     */
    private Instant findLatestTimeOfType(List<ClockEntry> entries, ClockType type) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).type() == type) {
                return entries.get(i).time().value();
            }
        }
        return null;
    }

    /**
     * 全休憩時間の合計（分）を計算する
     *
     * <p>打刻エントリ一覧からBREAK_START/BREAK_ENDのペアを順番に見つけ、
     * 各ペアの差分時間を合計する。</p>
     *
     * @param entries 打刻エントリ一覧
     * @return 合計休憩時間（分）
     */
    private int calculateTotalBreakMinutes(List<ClockEntry> entries) {
        int totalMinutes = 0;
        Instant breakStart = null;

        for (ClockEntry entry : entries) {
            if (entry.type() == ClockType.BREAK_START) {
                // 休憩開始時刻を記録する
                breakStart = entry.time().value();
            } else if (entry.type() == ClockType.BREAK_END && breakStart != null) {
                // 休憩開始〜終了の差分を加算する
                totalMinutes += (int) Duration.between(breakStart, entry.time().value()).toMinutes();
                breakStart = null;
            }
        }

        return totalMinutes;
    }
}
