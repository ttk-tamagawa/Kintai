package com.example.kintai.attendance.presentation.controller;

import com.example.kintai.attendance.application.command.WeeklyScheduleUseCase;
import com.example.kintai.attendance.application.command.WeeklyScheduleUseCase.AssignResult;
import com.example.kintai.attendance.application.query.WeeklyScheduleQueryService;
import com.example.kintai.attendance.domain.model.shift.WeeklySchedule;
import com.example.kintai.attendance.domain.repository.ShiftQueryRepository.ScheduleSummary;
import com.example.kintai.attendance.presentation.dto.AssignScheduleRequest;
import com.example.kintai.attendance.presentation.dto.ChangeScheduleRequest;
import com.example.kintai.attendance.presentation.dto.ScheduleResponse;
import com.example.kintai.attendance.presentation.dto.ScheduleResponse.DayAssignment;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.infrastructure.EmployeeAuthRepository;
import com.example.kintai.shared.infrastructure.EmployeeJpaEntity;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 週次スケジュールコントローラー — 5つのスケジュール管理APIエンドポイントを提供する
 *
 * <p>対応エンドポイント:
 * <ul>
 *   <li>POST /api/v1/shifts/schedules — スケジュール割当（5-7-1）</li>
 *   <li>PUT /api/v1/shifts/schedules/{scheduleId} — スケジュール変更（5-7-2）</li>
 *   <li>POST /api/v1/shifts/schedules/{scheduleId}/actions/publish — スケジュール公開（5-7-3）</li>
 *   <li>POST /api/v1/shifts/schedules/{scheduleId}/actions/unpublish — スケジュール非公開（5-7-3b）</li>
 *   <li>GET /api/v1/shifts/schedules — スケジュール一覧（5-7-4）</li>
 *   <li>GET /api/v1/shifts/schedules/{scheduleId} — スケジュール詳細（5-7-5）</li>
 * </ul>
 * </p>
 *
 * <p>コマンド系（割当・変更・公開）はWeeklyScheduleUseCaseに委譲し、
 * クエリ系（一覧・詳細）はWeeklyScheduleQueryServiceに委譲する。
 * コマンド実行後のレスポンスはクエリサービスで最新のRead Modelを再取得して返却する。</p>
 *
 * <p>assignments変換: APIは曜日名文字列（"MONDAY"等）でやり取りし、
 * コントローラーがDayOfWeek/ShiftPatternIdのドメイン型に変換する。</p>
 *
 * <p>認可: POST/PUT/publish→MANAGER, GET→EMPLOYEE/MANAGER</p>
 */
@RestController
@RequestMapping("/api/v1/shifts/schedules")
public class WeeklyScheduleController {

    private static final Logger log = LoggerFactory.getLogger(WeeklyScheduleController.class);

    /** 週次スケジュールユースケース — 書き込みユースケースの実行を委譲する */
    private final WeeklyScheduleUseCase commandService;

    /** 週次スケジュールクエリサービス — 読み取りユースケースの実行を委譲する */
    private final WeeklyScheduleQueryService queryService;

    /** 従業員リポジトリ — コマンドレスポンスで従業員名を取得するために使用する */
    private final EmployeeAuthRepository employeeRepository;

    public WeeklyScheduleController(
            WeeklyScheduleUseCase commandService,
            WeeklyScheduleQueryService queryService,
            EmployeeAuthRepository employeeRepository
    ) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.employeeRepository = employeeRepository;
    }

    // ========================================
    // POST / — スケジュール割当（5-7-1）
    // ========================================

    /**
     * 新しい週次スケジュールを割り当てる
     *
     * <p>処理フロー:
     * <ol>
     *   <li>リクエストのassignments（Map&lt;String, UUID&gt;）をドメイン型に変換する</li>
     *   <li>コマンドサービスで割当を実行する（重複チェック、パターンACTIVE検証含む）</li>
     *   <li>作成されたスケジュールをクエリサービスで再取得してレスポンスを組み立てる</li>
     * </ol>
     * </p>
     *
     * @param request 割当リクエスト（employeeId, weekStartDate, assignments）
     * @return 作成されたスケジュール情報（201 Created）
     */
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ScheduleResponse> assignSchedule(
            @Valid @RequestBody AssignScheduleRequest request) {

        log.debug("スケジュール割当リクエスト受信: employeeId={}, weekStartDate={}",
                request.employeeId(), request.weekStartDate());

        // リクエストの曜日文字列→DayOfWeek、UUID→ShiftPatternId に変換する
        Map<DayOfWeek, ShiftPatternId> assignments = parseAssignments(request.assignments());

        // コマンドサービスで割当を実行する（DRAFT状態で作成される）
        AssignResult result = commandService.assignSchedule(
                EmployeeId.of(request.employeeId()),
                request.weekStartDate(),
                assignments
        );

        // Write Modelから直接レスポンスを構築する（AFTER_COMMITプロジェクターのタイミング問題を回避）
        log.debug("スケジュール割当完了: scheduleId={}", result.schedule().getId().value());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toScheduleResponseFromWriteModel(result.schedule(), result.patternNames()));
    }

    // ========================================
    // PUT /{scheduleId} — スケジュール変更（5-7-2）
    // ========================================

    /**
     * 既存の週次スケジュールの割当を変更する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>リクエストのassignmentsをドメイン型に変換する</li>
     *   <li>コマンドサービスで変更を実行する（パターンACTIVE検証、PUBLISHEDならDRAFTに戻す）</li>
     *   <li>更新後のスケジュールをクエリサービスで再取得してレスポンスを組み立てる</li>
     * </ol>
     * </p>
     *
     * @param scheduleId スケジュールID（パスパラメータ）
     * @param request    変更リクエスト（assignments）
     * @return 変更後のスケジュール情報
     */
    @PutMapping("/{scheduleId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ScheduleResponse> changeSchedule(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody ChangeScheduleRequest request) {

        log.debug("スケジュール変更リクエスト受信: scheduleId={}", scheduleId);

        // リクエストの曜日文字列→DayOfWeek、UUID→ShiftPatternId に変換する
        Map<DayOfWeek, ShiftPatternId> assignments = parseAssignments(request.assignments());

        // コマンドサービスで変更を実行する（PUBLISHEDの場合はDRAFTに戻る）
        AssignResult result = commandService.changeSchedule(ScheduleId.of(scheduleId), assignments);

        // Write Modelから直接レスポンスを構築する（AFTER_COMMITプロジェクターのタイミング問題を回避）
        log.debug("スケジュール変更完了: scheduleId={}", scheduleId);
        return ResponseEntity.ok(toScheduleResponseFromWriteModel(result.schedule(), result.patternNames()));
    }

    // ========================================
    // POST /{scheduleId}/actions/publish — スケジュール公開（5-7-3）
    // ========================================

    /**
     * 週次スケジュールを公開する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>コマンドサービスで公開を実行する（DRAFTガード含む）</li>
     *   <li>公開後のスケジュールをクエリサービスで再取得してレスポンスを組み立てる</li>
     * </ol>
     * </p>
     *
     * @param scheduleId スケジュールID（パスパラメータ）
     * @return 公開後のスケジュール情報（status=PUBLISHED）
     */
    @PostMapping("/{scheduleId}/actions/publish")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ScheduleResponse> publishSchedule(@PathVariable UUID scheduleId) {
        log.debug("スケジュール公開リクエスト受信: scheduleId={}", scheduleId);

        // コマンドサービスで公開を実行する（DRAFT→PUBLISHED）
        WeeklySchedule schedule = commandService.publishSchedule(ScheduleId.of(scheduleId));

        // Write Modelから直接レスポンスを構築する（パターン名なし — 公開はステータス変更のみ）
        log.debug("スケジュール公開完了: scheduleId={}", scheduleId);
        return ResponseEntity.ok(toScheduleResponseFromWriteModel(schedule, Map.of()));
    }

    // ========================================
    // POST /{scheduleId}/actions/unpublish — スケジュール非公開（5-7-3b）
    // ========================================

    /**
     * 週次スケジュールを非公開にする
     *
     * <p>処理フロー:
     * <ol>
     *   <li>コマンドサービスで非公開を実行する（PUBLISHEDガード含む）</li>
     *   <li>非公開後のスケジュールをクエリサービスで再取得してレスポンスを組み立てる</li>
     * </ol>
     * </p>
     *
     * @param scheduleId スケジュールID（パスパラメータ）
     * @return 非公開後のスケジュール情報（status=DRAFT）
     */
    @PostMapping("/{scheduleId}/actions/unpublish")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ScheduleResponse> unpublishSchedule(@PathVariable UUID scheduleId) {
        log.debug("スケジュール非公開リクエスト受信: scheduleId={}", scheduleId);

        // コマンドサービスで非公開を実行する（PUBLISHED→DRAFT）
        WeeklySchedule schedule = commandService.unpublishSchedule(ScheduleId.of(scheduleId));

        // Write Modelから直接レスポンスを構築する（パターン名なし — 非公開はステータス変更のみ）
        log.debug("スケジュール非公開完了: scheduleId={}", scheduleId);
        return ResponseEntity.ok(toScheduleResponseFromWriteModel(schedule, Map.of()));
    }

    // ========================================
    // GET / — スケジュール一覧（5-7-4）
    // ========================================

    /**
     * 週次スケジュール一覧を取得する
     *
     * <p>従業員ID・期間でフィルタできる。
     * employeeIdが省略された場合は全従業員のスケジュールを返す（管理職向け）。
     * weekFrom/weekToが省略された場合は今週の月曜日〜4週先の日曜日がデフォルト。</p>
     *
     * @param employeeId 従業員ID（省略可。省略時は全従業員分を返却）
     * @param weekFrom   検索開始日（省略可。デフォルト: 今週の月曜日）
     * @param weekTo     検索終了日（省略可。デフォルト: 4週先の日曜日）
     * @return スケジュール一覧
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER')")
    public ResponseEntity<List<ScheduleResponse>> getSchedules(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) LocalDate weekFrom,
            @RequestParam(required = false) LocalDate weekTo) {

        log.debug("スケジュール一覧リクエスト受信: employeeId={}, weekFrom={}, weekTo={}", employeeId, weekFrom, weekTo);

        // クエリサービスでスケジュール一覧を取得する（employeeId=nullの場合は全件、weekFrom/weekToはサービス側でデフォルト値を設定）
        List<ScheduleSummary> schedules = queryService.getSchedules(employeeId, weekFrom, weekTo);

        // ScheduleSummary → ScheduleResponse に変換する
        List<ScheduleResponse> response = schedules.stream()
                .map(this::toScheduleResponse)
                .toList();

        log.debug("スケジュール一覧取得完了: {}件", response.size());
        return ResponseEntity.ok(response);
    }

    // ========================================
    // GET /{scheduleId} — スケジュール詳細（5-7-5）
    // ========================================

    /**
     * 週次スケジュールの詳細を取得する
     *
     * @param scheduleId スケジュールID（パスパラメータ）
     * @return スケジュール詳細
     */
    @GetMapping("/{scheduleId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER')")
    public ResponseEntity<ScheduleResponse> getSchedule(@PathVariable UUID scheduleId) {
        log.debug("スケジュール詳細リクエスト受信: scheduleId={}", scheduleId);

        // クエリサービスでスケジュール詳細を取得する（見つからなければ404）
        ScheduleSummary summary = queryService.getSchedule(scheduleId);

        log.debug("スケジュール詳細取得完了: employeeId={}, weekStartDate={}",
                summary.employeeId(), summary.weekStartDate());
        return ResponseEntity.ok(toScheduleResponse(summary));
    }

    // ========================================
    // ヘルパーメソッド
    // ========================================

    /**
     * リクエストの曜日文字列マップをドメイン型マップに変換する
     *
     * <p>JSONの {"MONDAY": "uuid", ...} を Map&lt;DayOfWeek, ShiftPatternId&gt; に変換する。
     * 無効な曜日名が含まれている場合は分かりやすいエラーメッセージを返す。</p>
     *
     * @param raw 曜日文字列→パターンUUIDのマップ
     * @return DayOfWeek→ShiftPatternIdのマップ
     * @throws IllegalArgumentException 無効な曜日名の場合
     */
    private Map<DayOfWeek, ShiftPatternId> parseAssignments(Map<String, UUID> raw) {
        Map<DayOfWeek, ShiftPatternId> result = new EnumMap<>(DayOfWeek.class);

        for (Map.Entry<String, UUID> entry : raw.entrySet()) {
            // 曜日文字列をDayOfWeekに変換する（大文字に正規化）
            DayOfWeek day;
            try {
                day = DayOfWeek.valueOf(entry.getKey().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "無効な曜日です: " + entry.getKey()
                                + "（MONDAY〜SUNDAYを指定してください）");
            }

            // UUIDをShiftPatternIdに変換する
            result.put(day, ShiftPatternId.of(entry.getValue()));
        }

        return result;
    }

    /**
     * ScheduleSummary（Read Model）→ ScheduleResponse（API応答）に変換する
     *
     * <p>ScheduleSummaryのフラットな7曜日×2カラム構造を、
     * 曜日名をキーとしたネストされたMap構造に変換する。
     * パターンが未割当の曜日（null）はMapに含めない。</p>
     *
     * @param summary クエリサービスから取得したスケジュール概要
     * @return APIレスポンスDTO
     */
    private ScheduleResponse toScheduleResponse(ScheduleSummary summary) {
        // 曜日ごとの割当をネストされたMapに変換する（割当がある曜日のみ）
        Map<String, DayAssignment> assignments = new LinkedHashMap<>();
        addDayIfPresent(assignments, "MONDAY", summary.mondayPatternId(), summary.mondayPatternName());
        addDayIfPresent(assignments, "TUESDAY", summary.tuesdayPatternId(), summary.tuesdayPatternName());
        addDayIfPresent(assignments, "WEDNESDAY", summary.wednesdayPatternId(), summary.wednesdayPatternName());
        addDayIfPresent(assignments, "THURSDAY", summary.thursdayPatternId(), summary.thursdayPatternName());
        addDayIfPresent(assignments, "FRIDAY", summary.fridayPatternId(), summary.fridayPatternName());
        addDayIfPresent(assignments, "SATURDAY", summary.saturdayPatternId(), summary.saturdayPatternName());
        addDayIfPresent(assignments, "SUNDAY", summary.sundayPatternId(), summary.sundayPatternName());

        return new ScheduleResponse(
                summary.scheduleId(),
                summary.employeeId(),
                summary.employeeName(),
                summary.weekStartDate(),
                summary.status(),
                assignments,
                summary.assignedDays(),
                summary.createdAt(),
                summary.updatedAt()
        );
    }

    /**
     * Write Model（WeeklySchedule）からScheduleResponseを直接構築する
     *
     * <p>AFTER_COMMITプロジェクターのタイミング問題を回避するため、
     * コマンド実行後はRead Modelに依存せずWrite Modelからレスポンスを返す。</p>
     *
     * @param schedule     保存済みの週次スケジュール
     * @param patternNames パターンID→パターン名のマップ
     * @return APIレスポンスDTO
     */
    private ScheduleResponse toScheduleResponseFromWriteModel(
            WeeklySchedule schedule, Map<ShiftPatternId, String> patternNames) {
        // 曜日ごとの割当をネストされたMapに変換する（割当がある曜日のみ）
        Map<String, DayAssignment> assignments = new LinkedHashMap<>();
        for (Map.Entry<DayOfWeek, ShiftPatternId> entry : schedule.getAssignments().entrySet()) {
            String dayName = entry.getKey().name();
            UUID patternId = entry.getValue().value();
            String patternName = patternNames.getOrDefault(entry.getValue(), "");
            assignments.put(dayName, new DayAssignment(patternId, patternName));
        }

        // 従業員テーブルから従業員名を取得する
        String employeeName = employeeRepository.findById(schedule.getEmployeeId().value())
                .map(EmployeeJpaEntity::getName)
                .orElse("");

        return new ScheduleResponse(
                schedule.getId().value(),
                schedule.getEmployeeId().value(),
                employeeName,
                schedule.getWeekStartDate(),
                schedule.getStatus().name(),
                assignments,
                schedule.getAssignments().size(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }

    /**
     * パターンIDが非nullの場合のみ、曜日の割当をMapに追加する
     *
     * @param assignments 追加先のMap
     * @param dayName     曜日名（MONDAY等）
     * @param patternId   パターンID（nullの場合は追加しない）
     * @param patternName パターン名
     */
    private void addDayIfPresent(Map<String, DayAssignment> assignments,
                                  String dayName, UUID patternId, String patternName) {
        if (patternId != null) {
            assignments.put(dayName, new DayAssignment(patternId, patternName));
        }
    }
}
