package com.example.kintai.attendance.presentation.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.kintai.attendance.application.query.DepartmentDashboardKpi;
import com.example.kintai.attendance.application.query.DepartmentDashboardResult;
import com.example.kintai.attendance.application.query.DepartmentDashboardRow;
import com.example.kintai.attendance.application.query.MonthlySummaryKpi;
import com.example.kintai.attendance.application.query.MonthlySummaryResult;
import com.example.kintai.attendance.application.query.MonthlySummaryRow;
import com.example.kintai.attendance.application.query.ExportDepartmentDashboardQuery;
import com.example.kintai.attendance.application.query.ExportDepartmentDashboardQueryService;
import com.example.kintai.attendance.application.query.ExportMonthlySummaryQuery;
import com.example.kintai.attendance.application.query.ExportMonthlySummaryQueryService;
import com.example.kintai.attendance.application.query.GetDailyAttendancesQuery;
import com.example.kintai.attendance.application.query.GetDailyAttendancesQueryService;
import com.example.kintai.attendance.application.query.GetDepartmentDashboardQuery;
import com.example.kintai.attendance.application.query.GetDepartmentDashboardQueryService;
import com.example.kintai.attendance.application.query.GetMonthlySummaryQuery;
import com.example.kintai.attendance.application.query.GetMonthlySummaryQueryService;
import com.example.kintai.attendance.application.query.GetTodayAttendanceQuery;
import com.example.kintai.attendance.application.query.GetTodayAttendanceQueryService;
import com.example.kintai.attendance.application.query.TodayAttendanceResult;
import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository.DailySummary;
import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository.PageResult;
import com.example.kintai.attendance.presentation.dto.DailyAttendancePageResponse;
import com.example.kintai.attendance.presentation.dto.DailyAttendancePageResponse.DailyAttendanceRow;
import com.example.kintai.attendance.presentation.dto.DepartmentDashboardResponse;
import com.example.kintai.attendance.presentation.dto.DepartmentDashboardResponse.DepartmentKpiDto;
import com.example.kintai.attendance.presentation.dto.DepartmentDashboardResponse.DepartmentRow;
import com.example.kintai.attendance.presentation.dto.DepartmentDashboardResponse.PagedDepartments;
import com.example.kintai.attendance.presentation.dto.MonthlySummaryResponse;
import com.example.kintai.attendance.presentation.dto.MonthlySummaryResponse.EmployeeRow;
import com.example.kintai.attendance.presentation.dto.MonthlySummaryResponse.MonthlySummaryKpiDto;
import com.example.kintai.attendance.presentation.dto.MonthlySummaryResponse.PagedEmployees;
import com.example.kintai.attendance.presentation.dto.TodayAttendanceResponse;
import com.example.kintai.shared.domain.model.EmployeeId;

/**
 * 勤怠記録クエリコントローラー — 6つの読み取りAPIエンドポイントを提供する
 *
 * <p>対応エンドポイント:
 * <ul>
 *   <li>GET /api/v1/attendances/today — 当日の勤怠ステータス取得</li>
 *   <li>GET /api/v1/attendances/daily — 日次勤怠一覧（ページネーション付き）</li>
 *   <li>GET /api/v1/attendances/monthly-summary — 月次サマリー（KPI + 従業員テーブル）</li>
 *   <li>GET /api/v1/attendances/monthly-summary/export — 月次サマリーCSV出力</li>
 *   <li>GET /api/v1/attendances/department-dashboard — 部門ダッシュボード（前月比較付き）</li>
 *   <li>GET /api/v1/attendances/department-dashboard/export — 部門ダッシュボードCSV出力</li>
 * </ul>
 * </p>
 *
 * <p>全エンドポイントはRead Model（CQRSの読み取り側）を参照する。
 * Write Model（集約）へのアクセスは行わない。</p>
 *
 * <p>認可: today→EMPLOYEE, daily→EMPLOYEE/MANAGER/HR, monthly-summary→MANAGER/HR, department-dashboard→HR/ADMIN</p>
 */
@RestController
@RequestMapping("/api/v1/attendances")
public class AttendanceQueryController {

    private static final Logger log = LoggerFactory.getLogger(AttendanceQueryController.class);

    /** 当日勤怠ステータス取得（UC-ATT-Q01） */
    private final GetTodayAttendanceQueryService getTodayAttendanceQueryService;

    /** 日次勤怠一覧取得（UC-ATT-Q02） */
    private final GetDailyAttendancesQueryService getDailyAttendancesQueryService;

    /** 月次サマリー取得（UC-ATT-Q03） */
    private final GetMonthlySummaryQueryService getMonthlySummaryQueryService;

    /** 月次サマリーCSV出力（UC-ATT-Q04） */
    private final ExportMonthlySummaryQueryService exportMonthlySummaryQueryService;

    /** 部門ダッシュボード取得（UC-ATT-Q05） */
    private final GetDepartmentDashboardQueryService getDepartmentDashboardQueryService;

    /** 部門ダッシュボードCSV出力（UC-ATT-Q06） */
    private final ExportDepartmentDashboardQueryService exportDepartmentDashboardQueryService;

    public AttendanceQueryController(
            GetTodayAttendanceQueryService getTodayAttendanceQueryService,
            GetDailyAttendancesQueryService getDailyAttendancesQueryService,
            GetMonthlySummaryQueryService getMonthlySummaryQueryService,
            ExportMonthlySummaryQueryService exportMonthlySummaryQueryService,
            GetDepartmentDashboardQueryService getDepartmentDashboardQueryService,
            ExportDepartmentDashboardQueryService exportDepartmentDashboardQueryService
    ) {
        this.getTodayAttendanceQueryService = getTodayAttendanceQueryService;
        this.getDailyAttendancesQueryService = getDailyAttendancesQueryService;
        this.getMonthlySummaryQueryService = getMonthlySummaryQueryService;
        this.exportMonthlySummaryQueryService = exportMonthlySummaryQueryService;
        this.getDepartmentDashboardQueryService = getDepartmentDashboardQueryService;
        this.exportDepartmentDashboardQueryService = exportDepartmentDashboardQueryService;
    }

    // ========================================
    // GET /today — 当日の勤怠ステータス取得
    // ========================================

    /**
     * 当日の勤怠ステータスを取得する
     *
     * <p>勤怠打刻モーダル（SCR-ATT-001）の初期表示に使用する。
     * 当日の勤怠記録がない場合は 204 No Content を返す。</p>
     *
     * @param employeeId 従業員ID（クエリパラメータ）
     * @return 当日の勤怠ステータス（200）、または未出勤（204）
     */
    @GetMapping("/today")
    @PreAuthorize("hasRole('EMPLOYEE') and @accessControl.canAccessEmployee(authentication, #employeeId)")
    public ResponseEntity<TodayAttendanceResponse> getTodayAttendance(
            @RequestParam String employeeId) {

        log.debug("当日勤怠取得リクエスト受信: employeeId={}", employeeId);

        // 当日勤怠ステータス取得クエリを実行する
        return getTodayAttendanceQueryService.execute(new GetTodayAttendanceQuery(EmployeeId.of(employeeId)))
                .map(result -> {
                    // サービス結果をレスポンスDTOに変換する
                    TodayAttendanceResponse response = toTodayAttendanceResponse(result);
                    log.debug("当日勤怠取得完了: attendanceId={}", result.attendanceId());
                    return ResponseEntity.ok(response);
                })
                // 当日の勤怠記録がない場合は 204 を返す
                .orElseGet(() -> {
                    log.debug("当日勤怠なし: employeeId={}", employeeId);
                    return ResponseEntity.noContent().build();
                });
    }

    // ========================================
    // GET /daily — 日次勤怠一覧
    // ========================================

    /**
     * 日次勤怠一覧をページネーション付きで取得する
     *
     * <p>日次勤怠一覧画面（SCR-ATT-002）に使用する。
     * 日付範囲、ステータスフィルタ、ソート、ページネーションに対応する。</p>
     *
     * @param employeeId    従業員ID
     * @param dateFrom      期間開始日（省略時: 当月1日）
     * @param dateTo        期間終了日（省略時: 当月末日）
     * @param status        ステータスフィルタ（省略時: 全ステータス）
     * @param page          ページ番号（0始まり、デフォルト: 0）
     * @param size          1ページあたりの件数（デフォルト: 20）
     * @param sortField     ソートフィールド（デフォルト: workDate）
     * @param sortDirection ソート方向（デフォルト: desc）
     * @return ページネーション付き日次勤怠一覧
     */
    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'HR') and @accessControl.canAccessEmployee(authentication, #employeeId)")
    public ResponseEntity<DailyAttendancePageResponse> getDailyAttendances(
            @RequestParam String employeeId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "workDate") String sortField,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        log.debug("日次勤怠一覧リクエスト受信: employeeId={}, from={}, to={}", employeeId, dateFrom, dateTo);

        // 日次勤怠一覧取得クエリを実行する
        PageResult<DailySummary> result = getDailyAttendancesQueryService.execute(
                new GetDailyAttendancesQuery(EmployeeId.of(employeeId), dateFrom, dateTo, status, page, size, sortField, sortDirection));

        // サービス結果をレスポンスDTOに変換する
        DailyAttendancePageResponse response = toDailyAttendancePageResponse(result);

        log.debug("日次勤怠一覧取得完了: totalElements={}", result.totalElements());
        return ResponseEntity.ok(response);
    }

    // ========================================
    // GET /monthly-summary — 月次サマリー
    // ========================================

    /**
     * 月次勤怠サマリーを取得する（KPI + 従業員別テーブル）
     *
     * <p>月次勤怠サマリー画面（SCR-ATT-003）に使用する。
     * KPIは全従業員データから集計し、テーブルはページネーション付きで返却する。</p>
     *
     * @param departmentId  部門ID
     * @param year          年（省略時: 当年）
     * @param month         月（省略時: 当月）
     * @param page          ページ番号（0始まり、デフォルト: 0）
     * @param size          1ページあたりの件数（デフォルト: 20）
     * @param sortField     ソートフィールド（デフォルト: employeeName）
     * @param sortDirection ソート方向（デフォルト: asc）
     * @return KPI + 従業員別テーブルの月次サマリー
     */
    @GetMapping("/monthly-summary")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR') and @accessControl.canAccessDepartment(authentication, #departmentId)")
    public ResponseEntity<MonthlySummaryResponse> getMonthlySummary(
            @RequestParam String departmentId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "employeeName") String sortField,
            @RequestParam(defaultValue = "asc") String sortDirection) {

        // 年月が省略された場合は当月をデフォルトにする
        YearMonth now = YearMonth.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();

        log.debug("月次サマリーリクエスト受信: departmentId={}, year={}, month={}", departmentId, y, m);

        // 月次サマリー取得クエリを実行する
        MonthlySummaryResult result = getMonthlySummaryQueryService.execute(
                new GetMonthlySummaryQuery(departmentId, y, m, page, size, sortField, sortDirection));

        // サービス結果をレスポンスDTOに変換する
        MonthlySummaryResponse response = toMonthlySummaryResponse(result);

        log.debug("月次サマリー取得完了: totalElements={}", result.employees().totalElements());
        return ResponseEntity.ok(response);
    }

    // ========================================
    // GET /monthly-summary/export — 月次サマリーCSV出力
    // ========================================

    /**
     * 月次勤怠サマリーをCSV形式でエクスポートする
     *
     * <p>全件出力（ページネーションなし）。
     * Content-Typeは text/csv、Content-Dispositionにファイル名を設定する。</p>
     *
     * @param departmentId 部門ID
     * @param year         年（省略時: 当年）
     * @param month        月（省略時: 当月）
     * @return CSVファイル（UTF-8 BOM付き）
     */
    @GetMapping("/monthly-summary/export")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR') and @accessControl.canAccessDepartment(authentication, #departmentId)")
    public ResponseEntity<byte[]> exportMonthlySummary(
            @RequestParam String departmentId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        // 年月が省略された場合は当月をデフォルトにする
        YearMonth now = YearMonth.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();

        log.debug("月次サマリーCSVエクスポート: departmentId={}, year={}, month={}", departmentId, y, m);

        // 月次サマリーCSV出力クエリを実行する
        byte[] csvBytes = exportMonthlySummaryQueryService.execute(
                new ExportMonthlySummaryQuery(departmentId, y, m));

        // CSVファイル名を生成する（例: monthly_summary_2026_02.csv）
        String filename = String.format("monthly_summary_%04d_%02d.csv", y, m);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(csvBytes.length)
                .body(csvBytes);
    }

    // ========================================
    // GET /department-dashboard — 部門ダッシュボード
    // ========================================

    /**
     * 部門別勤怠ダッシュボードを取得する（KPI + 前月比較 + 部門テーブル）
     *
     * <p>部門別勤怠ダッシュボード画面（SCR-ATT-004）に使用する。
     * KPIは前月比トレンド付きで、テーブルはページネーション付きで返却する。</p>
     *
     * @param departmentId  部門IDフィルタ（省略時: 全部門）
     * @param year          年（省略時: 当年）
     * @param month         月（省略時: 当月）
     * @param page          ページ番号（0始まり、デフォルト: 0）
     * @param size          1ページあたりの件数（デフォルト: 20）
     * @param sortField     ソートフィールド（デフォルト: departmentName）
     * @param sortDirection ソート方向（デフォルト: asc）
     * @return KPI + 前月KPI + 部門別テーブルのダッシュボード
     */
    @GetMapping("/department-dashboard")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<DepartmentDashboardResponse> getDepartmentDashboard(
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "departmentName") String sortField,
            @RequestParam(defaultValue = "asc") String sortDirection) {

        // 年月が省略された場合は当月をデフォルトにする
        YearMonth now = YearMonth.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();

        log.debug("部門ダッシュボードリクエスト受信: departmentId={}, year={}, month={}", departmentId, y, m);

        // 部門ダッシュボード取得クエリを実行する
        DepartmentDashboardResult result = getDepartmentDashboardQueryService.execute(
                new GetDepartmentDashboardQuery(departmentId, y, m, page, size, sortField, sortDirection));

        // サービス結果をレスポンスDTOに変換する
        DepartmentDashboardResponse response = toDepartmentDashboardResponse(result);

        log.debug("部門ダッシュボード取得完了: totalElements={}", result.departments().totalElements());
        return ResponseEntity.ok(response);
    }

    // ========================================
    // GET /department-dashboard/export — 部門ダッシュボードCSV出力
    // ========================================

    /**
     * 部門別勤怠ダッシュボードをCSV形式でエクスポートする
     *
     * <p>全件出力（ページネーションなし）。
     * Content-Typeは text/csv、Content-Dispositionにファイル名を設定する。</p>
     *
     * @param departmentId 部門IDフィルタ（省略時: 全部門）
     * @param year         年（省略時: 当年）
     * @param month        月（省略時: 当月）
     * @return CSVファイル（UTF-8 BOM付き）
     */
    @GetMapping("/department-dashboard/export")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<byte[]> exportDepartmentDashboard(
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        // 年月が省略された場合は当月をデフォルトにする
        YearMonth now = YearMonth.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();

        log.debug("部門ダッシュボードCSVエクスポート: departmentId={}, year={}, month={}", departmentId, y, m);

        // 部門ダッシュボードCSV出力クエリを実行する
        byte[] csvBytes = exportDepartmentDashboardQueryService.execute(
                new ExportDepartmentDashboardQuery(departmentId, y, m));

        // CSVファイル名を生成する（例: department_dashboard_2026_02.csv）
        String filename = String.format("department_dashboard_%04d_%02d.csv", y, m);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(csvBytes.length)
                .body(csvBytes);
    }

    // ========================================
    // DTO変換ヘルパーメソッド
    // ========================================

    /** TodayAttendanceResult → TodayAttendanceResponse に変換する */
    private TodayAttendanceResponse toTodayAttendanceResponse(TodayAttendanceResult result) {
        return new TodayAttendanceResponse(
                result.attendanceId(),
                result.employeeId(),
                result.workDate(),
                result.status(),
                result.onBreak(),
                result.clockIn(),
                result.clockOut(),
                result.breakMinutes(),
                result.netWorkMinutes(),
                result.totalOvertimeMinutes(),
                result.updatedAt()
        );
    }

    /** PageResult<DailySummary> → DailyAttendancePageResponse に変換する */
    private DailyAttendancePageResponse toDailyAttendancePageResponse(PageResult<DailySummary> result) {
        // DailySummary → DailyAttendanceRow に変換する
        List<DailyAttendanceRow> rows = result.content().stream()
                .map(s -> new DailyAttendanceRow(
                        s.attendanceId(),
                        s.employeeId(),
                        s.workDate(),
                        s.status(),
                        s.clockInTime(),
                        s.clockOutTime(),
                        s.scheduledMinutes(),
                        s.actualMinutes(),
                        s.breakMinutes(),
                        s.netWorkMinutes(),
                        s.regularOvertimeMinutes(),
                        s.lateNightMinutes(),
                        s.holidayMinutes(),
                        s.totalOvertimeMinutes(),
                        s.updatedAt()
                ))
                .toList();

        return new DailyAttendancePageResponse(
                rows, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    /** MonthlySummaryResult → MonthlySummaryResponse に変換する */
    private MonthlySummaryResponse toMonthlySummaryResponse(MonthlySummaryResult result) {
        // KPIを変換する
        MonthlySummaryKpi kpi = result.kpi();
        MonthlySummaryKpiDto kpiDto = new MonthlySummaryKpiDto(
                kpi.totalWorkDays(), kpi.avgWorkDays(),
                kpi.totalWorkHours(), kpi.avgWorkHours(),
                kpi.totalOvertimeHours(), kpi.avgOvertimeHours(),
                kpi.totalPaidLeaveUsed()
        );

        // 従業員テーブルを変換する
        PageResult<MonthlySummaryRow> employees = result.employees();
        List<EmployeeRow> employeeRows = employees.content().stream()
                .map(r -> new EmployeeRow(
                        r.employeeId(), r.employeeName(), r.workDays(),
                        r.totalWorkHours(), r.totalOvertimeHours(),
                        r.lateNightHours(), r.paidLeaveUsed()
                ))
                .toList();
        PagedEmployees pagedEmployees = new PagedEmployees(
                employeeRows, employees.page(), employees.size(),
                employees.totalElements(), employees.totalPages());

        return new MonthlySummaryResponse(kpiDto, pagedEmployees);
    }

    /** DepartmentDashboardResult → DepartmentDashboardResponse に変換する */
    private DepartmentDashboardResponse toDepartmentDashboardResponse(DepartmentDashboardResult result) {
        // 当月KPIを変換する
        DepartmentKpiDto currentKpi = toDepartmentKpiDto(result.kpi());

        // 前月KPIを変換する
        DepartmentKpiDto previousKpi = toDepartmentKpiDto(result.previousMonth());

        // 部門テーブルを変換する
        PageResult<DepartmentDashboardRow> departments = result.departments();
        List<DepartmentRow> departmentRows = departments.content().stream()
                .map(d -> new DepartmentRow(
                        d.departmentId(), d.departmentName(), d.headCount(),
                        d.avgOvertimeHours(), d.maxOvertimeHours(),
                        d.overtimeAlertCount(), d.missingClockCount(),
                        d.attendanceRate()
                ))
                .toList();
        PagedDepartments pagedDepartments = new PagedDepartments(
                departmentRows, departments.page(), departments.size(),
                departments.totalElements(), departments.totalPages());

        return new DepartmentDashboardResponse(currentKpi, previousKpi, pagedDepartments);
    }

    /** DepartmentDashboardKpi → DepartmentKpiDto に変換する */
    private DepartmentKpiDto toDepartmentKpiDto(DepartmentDashboardKpi kpi) {
        return new DepartmentKpiDto(
                kpi.avgOvertimeHours(),
                kpi.totalOvertimeAlertCount(),
                kpi.totalMissingClockCount(),
                kpi.avgAttendanceRate()
        );
    }
}
