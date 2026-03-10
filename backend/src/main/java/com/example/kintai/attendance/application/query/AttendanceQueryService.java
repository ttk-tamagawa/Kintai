package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository;
import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository.DailySummary;
import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository.DepartmentStats;
import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository.MonthlySummary;
import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository.PageResult;
import com.example.kintai.shared.domain.model.EmployeeId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 勤怠記録クエリサービス — 勤怠記録の6つの読み取りユースケースを統合するアプリケーションサービス
 *
 * <p>CQRS（コマンドクエリ責務分離）のクエリ側を担当し、
 * Read Model（attendance_summaries, monthly_attendance_summaries, department_attendance_stats）
 * からデータを読み取り、画面表示用に加工して返却する。</p>
 *
 * <p>対応ユースケース:
 * <ul>
 *   <li>UC-ATT-Q01: 当日の勤怠ステータスを取得する（getTodayAttendance）</li>
 *   <li>UC-ATT-Q02: 日次勤怠一覧を取得する（getDailyAttendances）</li>
 *   <li>UC-ATT-Q03: 月次勤怠サマリーを取得する（getMonthlySummary）</li>
 *   <li>UC-ATT-Q04: 月次勤怠サマリーをCSV出力する（exportMonthlySummary）</li>
 *   <li>UC-ATT-Q05: 部門別勤怠ダッシュボードを取得する（getDepartmentDashboard）</li>
 *   <li>UC-ATT-Q06: 部門別勤怠ダッシュボードをCSV出力する（exportDepartmentDashboard）</li>
 * </ul>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class AttendanceQueryService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceQueryService.class);

    /** UTF-8 BOM — ExcelでのCSV文字化け防止 */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Read Modelクエリリポジトリ */
    private final AttendanceSummaryQueryRepository queryRepository;

    /** Write Modelリポジトリ（休憩中フラグの判定に使用） */
    private final AttendanceRecordRepository attendanceRecordRepository;

    public AttendanceQueryService(AttendanceSummaryQueryRepository queryRepository,
                                  AttendanceRecordRepository attendanceRecordRepository) {
        this.queryRepository = queryRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    // ========================
    // 結果型定義
    // ========================

    /**
     * 当日勤怠ステータス — 勤怠打刻モーダルの初期表示データ
     */
    public record TodayAttendanceResult(
            UUID attendanceId,
            String employeeId,
            LocalDate workDate,
            String status,
            boolean onBreak,
            Instant clockIn,
            Instant clockOut,
            int breakMinutes,
            Integer netWorkMinutes,
            Integer totalOvertimeMinutes,
            Instant updatedAt
    ) {}

    /**
     * 月次サマリーKPI — 画面上部のKPIカード4枚分のデータ
     */
    public record MonthlySummaryKpi(
            int totalWorkDays,
            double avgWorkDays,
            double totalWorkHours,
            double avgWorkHours,
            double totalOvertimeHours,
            double avgOvertimeHours,
            double totalPaidLeaveUsed
    ) {}

    /**
     * 月次サマリー結果 — KPI + 従業員別テーブル（ページネーション付き）
     */
    public record MonthlySummaryResult(
            MonthlySummaryKpi kpi,
            PageResult<MonthlySummaryRow> employees
    ) {}

    /**
     * 月次サマリー行 — 従業員別テーブルの1行（分→時間変換済み）
     */
    public record MonthlySummaryRow(
            String employeeId,
            String employeeName,
            int workDays,
            double totalWorkHours,
            double totalOvertimeHours,
            double lateNightHours,
            double paidLeaveUsed
    ) {}

    /**
     * 部門ダッシュボードKPI — 画面上部のKPIカード4枚分のデータ
     */
    public record DepartmentDashboardKpi(
            double avgOvertimeHours,
            int totalOvertimeAlertCount,
            int totalMissingClockCount,
            double avgAttendanceRate
    ) {}

    /**
     * 部門ダッシュボード結果 — 当月KPI + 前月KPI + 部門別テーブル（ページネーション付き）
     */
    public record DepartmentDashboardResult(
            DepartmentDashboardKpi kpi,
            DepartmentDashboardKpi previousMonth,
            PageResult<DepartmentDashboardRow> departments
    ) {}

    /**
     * 部門ダッシュボード行 — 部門別テーブルの1行（分→時間変換済み）
     */
    public record DepartmentDashboardRow(
            String departmentId,
            String departmentName,
            int headCount,
            double avgOvertimeHours,
            double maxOvertimeHours,
            int overtimeAlertCount,
            int missingClockCount,
            double attendanceRate
    ) {}

    // ========================================
    // UC-ATT-Q01: 当日の勤怠ステータスを取得する
    // ========================================

    /**
     * 当日の勤怠ステータスを取得する
     *
     * <p>勤怠打刻モーダル（SCR-ATT-001）の初期表示に使用する。
     * 当日の勤怠記録がない場合は空を返す。</p>
     *
     * @param employeeId 従業員ID
     * @return 当日の勤怠ステータス（存在しない場合はempty）
     */
    public Optional<TodayAttendanceResult> getTodayAttendance(EmployeeId employeeId) {
        LocalDate today = LocalDate.now();
        log.debug("当日勤怠取得: employeeId={}, date={}", employeeId.value(), today);

        // 当日の日次サマリーを取得する（from=to=today で1件取得）
        List<DailySummary> summaries = queryRepository.findDailySummaries(employeeId, today, today);

        // 当日分がない場合は空を返す
        if (summaries.isEmpty()) {
            return Optional.empty();
        }

        // DailySummary → TodayAttendanceResult に変換する
        DailySummary s = summaries.getFirst();

        // 書き込みモデルから休憩中フラグを取得する（打刻エントリの BREAK_START/END 数で判定）
        boolean onBreak = attendanceRecordRepository
                .findByEmployeeIdAndWorkDate(employeeId, new WorkDate(today))
                .map(AttendanceRecord::isOnBreak)
                .orElse(false);

        return Optional.of(new TodayAttendanceResult(
                s.attendanceId(),
                s.employeeId(),
                s.workDate(),
                s.status(),
                onBreak,
                s.clockInTime(),
                s.clockOutTime(),
                s.breakMinutes(),
                // 未退勤の場合はnull（計算未実施）
                s.netWorkMinutes() > 0 ? s.netWorkMinutes() : null,
                s.totalOvertimeMinutes() > 0 ? s.totalOvertimeMinutes() : null,
                s.updatedAt()
        ));
    }

    // ========================================
    // UC-ATT-Q02: 日次勤怠一覧を取得する
    // ========================================

    /**
     * 日次勤怠一覧をページネーション付きで取得する
     *
     * <p>日次勤怠一覧画面（SCR-ATT-002）に使用する。
     * ステータスフィルタ、ソート、ページネーションに対応する。</p>
     *
     * @param employeeId    従業員ID
     * @param dateFrom      期間開始日（nullの場合は当月1日）
     * @param dateTo        期間終了日（nullの場合は当月末日）
     * @param status        ステータスフィルタ（nullの場合は全ステータス）
     * @param page          ページ番号（0始まり）
     * @param size          1ページあたりの件数
     * @param sortField     ソートフィールド（workDate, totalOvertimeMinutes, status）
     * @param sortDirection ソート方向（asc, desc）
     * @return ページネーション結果
     */
    public PageResult<DailySummary> getDailyAttendances(
            EmployeeId employeeId, LocalDate dateFrom, LocalDate dateTo,
            String status, int page, int size, String sortField, String sortDirection) {

        // デフォルト値を設定する（nullの場合は当月範囲）
        YearMonth currentMonth = YearMonth.now();
        LocalDate from = dateFrom != null ? dateFrom : currentMonth.atDay(1);
        LocalDate to = dateTo != null ? dateTo : currentMonth.atEndOfMonth();
        String sort = sortField != null ? sortField : "workDate";
        String dir = sortDirection != null ? sortDirection : "desc";

        log.debug("日次勤怠一覧: employeeId={}, from={}, to={}, status={}, page={}, size={}",
                employeeId.value(), from, to, status, page, size);

        // ページネーション付きで日次サマリーを取得する
        return queryRepository.findDailySummariesPaged(
                employeeId, from, to, status, page, size, sort, dir);
    }

    // ========================================
    // UC-ATT-Q03: 月次勤怠サマリーを取得する
    // ========================================

    /**
     * 月次勤怠サマリーを取得する（KPI + 従業員別テーブル）
     *
     * <p>月次勤怠サマリー画面（SCR-ATT-003）に使用する。
     * KPIは全従業員データから集計し、テーブルはページネーション付きで返却する。</p>
     *
     * @param departmentId  部門ID
     * @param year          年
     * @param month         月
     * @param page          ページ番号（0始まり）
     * @param size          1ページあたりの件数
     * @param sortField     ソートフィールド（employeeName, totalWorkHours, totalOvertimeHours）
     * @param sortDirection ソート方向（asc, desc）
     * @return KPI + 従業員別テーブルの結果
     */
    public MonthlySummaryResult getMonthlySummary(
            String departmentId, int year, int month,
            int page, int size, String sortField, String sortDirection) {

        String sort = sortField != null ? sortField : "employeeName";
        String dir = sortDirection != null ? sortDirection : "asc";

        log.debug("月次サマリー: departmentId={}, year={}, month={}, page={}, size={}",
                departmentId, year, month, page, size);

        // 全従業員データを取得してKPIを集計する（ページネーション前の全データ）
        List<MonthlySummary> allSummaries =
                queryRepository.findMonthlySummariesByDepartment(departmentId, year, month);
        MonthlySummaryKpi kpi = aggregateMonthlySummaryKpi(allSummaries);

        // ページネーション付きで従業員別データを取得する
        PageResult<MonthlySummary> pagedRaw =
                queryRepository.findMonthlySummariesByDepartmentPaged(
                        departmentId, year, month, page, size, sort, dir);

        // 分→時間に変換した行データを作成する
        List<MonthlySummaryRow> rows = pagedRaw.content().stream()
                .map(this::toMonthlySummaryRow)
                .toList();
        PageResult<MonthlySummaryRow> pagedRows = PageResult.of(
                rows, pagedRaw.page(), pagedRaw.size(), pagedRaw.totalElements());

        return new MonthlySummaryResult(kpi, pagedRows);
    }

    // ========================================
    // UC-ATT-Q04: 月次勤怠サマリーをCSV出力する
    // ========================================

    /**
     * 月次勤怠サマリーをCSV形式で出力する
     *
     * <p>全件出力（ページネーションなし）。
     * UTF-8 BOM付きでExcelの文字化けを防止する。</p>
     *
     * @param departmentId 部門ID
     * @param year         年
     * @param month        月
     * @return CSV形式のバイト配列（UTF-8 BOM付き）
     */
    public byte[] exportMonthlySummary(String departmentId, int year, int month) {
        log.debug("月次サマリーCSV: departmentId={}, year={}, month={}", departmentId, year, month);

        // 全従業員データを取得する（ページネーションなし）
        List<MonthlySummary> summaries =
                queryRepository.findMonthlySummariesByDepartment(departmentId, year, month);

        // CSV文字列を組み立てる
        StringBuilder csv = new StringBuilder();
        // ヘッダー行
        csv.append("従業員ID,従業員名,出勤日数,総労働時間,総残業時間,深夜時間,有給消化\n");
        // データ行
        for (MonthlySummary s : summaries) {
            csv.append(s.employeeId()).append(',');
            csv.append(escapeCsv(s.employeeName())).append(',');
            csv.append(s.totalWorkDays()).append(',');
            csv.append(minutesToHours(s.totalWorkMinutes())).append(',');
            csv.append(minutesToHours(s.totalOvertimeMinutes())).append(',');
            csv.append(minutesToHours(s.totalLateNightMinutes())).append(',');
            csv.append(s.paidLeaveUsed()).append('\n');
        }

        // UTF-8 BOM + CSV本文をバイト配列に変換する
        return addBom(csv.toString());
    }

    // ========================================
    // UC-ATT-Q05: 部門別勤怠ダッシュボードを取得する
    // ========================================

    /**
     * 部門別勤怠ダッシュボードを取得する（KPI + 前月KPI + 部門別テーブル）
     *
     * <p>部門別勤怠ダッシュボード画面（SCR-ATT-004）に使用する。
     * KPIは前月比トレンド付きで、テーブルはページネーション付きで返却する。</p>
     *
     * @param departmentId  部門IDフィルタ（nullの場合は全部門）
     * @param year          年
     * @param month         月
     * @param page          ページ番号（0始まり）
     * @param size          1ページあたりの件数
     * @param sortField     ソートフィールド
     * @param sortDirection ソート方向（asc, desc）
     * @return KPI + 前月KPI + 部門別テーブルの結果
     */
    public DepartmentDashboardResult getDepartmentDashboard(
            String departmentId, int year, int month,
            int page, int size, String sortField, String sortDirection) {

        log.debug("部門ダッシュボード: departmentId={}, year={}, month={}, page={}, size={}",
                departmentId, year, month, page, size);

        // 当月の部門統計を取得する
        List<DepartmentStats> currentStats = queryRepository.findDepartmentStats(year, month);

        // 部門IDフィルタが指定されている場合は絞り込む
        if (departmentId != null && !departmentId.isEmpty()) {
            currentStats = currentStats.stream()
                    .filter(s -> departmentId.equals(s.departmentId()))
                    .toList();
        }

        // 当月のKPIを集計する
        DepartmentDashboardKpi currentKpi = aggregateDepartmentKpi(currentStats);

        // 前月の部門統計を取得してKPIを集計する
        YearMonth prevMonth = YearMonth.of(year, month).minusMonths(1);
        List<DepartmentStats> prevStats = queryRepository.findDepartmentStats(
                prevMonth.getYear(), prevMonth.getMonthValue());
        if (departmentId != null && !departmentId.isEmpty()) {
            prevStats = prevStats.stream()
                    .filter(s -> departmentId.equals(s.departmentId()))
                    .toList();
        }
        DepartmentDashboardKpi previousKpi = aggregateDepartmentKpi(prevStats);

        // ソートを適用してページネーションする（部門数は少ないためインメモリ処理）
        List<DepartmentDashboardRow> allRows = currentStats.stream()
                .map(this::toDepartmentDashboardRow)
                .toList();
        List<DepartmentDashboardRow> sortedRows = sortDepartmentRows(
                allRows, sortField, sortDirection);
        PageResult<DepartmentDashboardRow> pagedRows = paginateInMemory(
                sortedRows, page, size);

        return new DepartmentDashboardResult(currentKpi, previousKpi, pagedRows);
    }

    // ========================================
    // UC-ATT-Q06: 部門別ダッシュボードをCSV出力する
    // ========================================

    /**
     * 部門別勤怠ダッシュボードをCSV形式で出力する
     *
     * <p>全件出力（ページネーションなし）。
     * UTF-8 BOM付きでExcelの文字化けを防止する。</p>
     *
     * @param departmentId 部門IDフィルタ（nullの場合は全部門）
     * @param year         年
     * @param month        月
     * @return CSV形式のバイト配列（UTF-8 BOM付き）
     */
    public byte[] exportDepartmentDashboard(String departmentId, int year, int month) {
        log.debug("部門ダッシュボードCSV: departmentId={}, year={}, month={}", departmentId, year, month);

        // 全部門統計を取得する
        List<DepartmentStats> stats = queryRepository.findDepartmentStats(year, month);

        // 部門IDフィルタが指定されている場合は絞り込む
        if (departmentId != null && !departmentId.isEmpty()) {
            stats = stats.stream()
                    .filter(s -> departmentId.equals(s.departmentId()))
                    .toList();
        }

        // CSV文字列を組み立てる
        StringBuilder csv = new StringBuilder();
        // ヘッダー行
        csv.append("部署ID,部署名,人数,平均残業時間,最大残業時間,残業アラート件数,打刻漏れ件数,出勤率\n");
        // データ行
        for (DepartmentStats s : stats) {
            csv.append(s.departmentId()).append(',');
            csv.append(escapeCsv(s.departmentName())).append(',');
            csv.append(s.totalEmployees()).append(',');
            csv.append(bigDecimalMinutesToHours(s.avgOvertimeMinutes())).append(',');
            csv.append(minutesToHours(s.maxOvertimeMinutes())).append(',');
            csv.append(s.overtimeAlertCount()).append(',');
            csv.append(s.missingClockCount()).append(',');
            csv.append(formatRate(s.attendanceRate())).append('\n');
        }

        // UTF-8 BOM + CSV本文をバイト配列に変換する
        return addBom(csv.toString());
    }

    // ========================================
    // KPI集計ヘルパー
    // ========================================

    /**
     * 月次サマリーからKPI（4枚のカード）を集計する
     *
     * <p>全従業員データから合計・平均を算出する。
     * DBのminutes値をhours（小数第1位）に変換する。</p>
     */
    private MonthlySummaryKpi aggregateMonthlySummaryKpi(List<MonthlySummary> summaries) {
        if (summaries.isEmpty()) {
            return new MonthlySummaryKpi(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        int count = summaries.size();

        // 各項目の合計を算出する
        int totalWorkDays = summaries.stream().mapToInt(MonthlySummary::totalWorkDays).sum();
        int totalWorkMinutes = summaries.stream().mapToInt(MonthlySummary::totalWorkMinutes).sum();
        int totalOvertimeMinutes = summaries.stream().mapToInt(MonthlySummary::totalOvertimeMinutes).sum();
        double totalPaidLeave = summaries.stream()
                .map(MonthlySummary::paidLeaveUsed)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();

        // 平均を算出し、分→時間に変換する
        return new MonthlySummaryKpi(
                totalWorkDays,
                round1((double) totalWorkDays / count),
                round1((double) totalWorkMinutes / 60.0),
                round1((double) totalWorkMinutes / count / 60.0),
                round1((double) totalOvertimeMinutes / 60.0),
                round1((double) totalOvertimeMinutes / count / 60.0),
                round1(totalPaidLeave)
        );
    }

    /**
     * 部門統計からダッシュボードKPI（4枚のカード）を集計する
     *
     * <p>全部門データから平均・合計を算出する。
     * DBのminutes値をhours（小数第1位）に変換する。</p>
     */
    private DepartmentDashboardKpi aggregateDepartmentKpi(List<DepartmentStats> stats) {
        if (stats.isEmpty()) {
            return new DepartmentDashboardKpi(0.0, 0, 0, 0.0);
        }

        // 全部門の平均残業時間を算出する（各部門の平均を全部門で平均）
        double avgOvertimeMinutes = stats.stream()
                .map(DepartmentStats::avgOvertimeMinutes)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);

        // 残業アラート・打刻漏れの合計を算出する
        int totalAlerts = stats.stream().mapToInt(DepartmentStats::overtimeAlertCount).sum();
        int totalMissing = stats.stream().mapToInt(DepartmentStats::missingClockCount).sum();

        // 全部門の平均出勤率を算出する
        double avgRate = stats.stream()
                .map(DepartmentStats::attendanceRate)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);

        return new DepartmentDashboardKpi(
                round1(avgOvertimeMinutes / 60.0),
                totalAlerts,
                totalMissing,
                round1(avgRate)
        );
    }

    // ========================================
    // 変換・ソート・ページネーション ヘルパー
    // ========================================

    /** MonthlySummary → MonthlySummaryRow に変換する（分→時間変換） */
    private MonthlySummaryRow toMonthlySummaryRow(MonthlySummary s) {
        return new MonthlySummaryRow(
                s.employeeId(),
                s.employeeName(),
                s.totalWorkDays(),
                round1((double) s.totalWorkMinutes() / 60.0),
                round1((double) s.totalOvertimeMinutes() / 60.0),
                round1((double) s.totalLateNightMinutes() / 60.0),
                s.paidLeaveUsed().doubleValue()
        );
    }

    /** DepartmentStats → DepartmentDashboardRow に変換する（分→時間変換） */
    private DepartmentDashboardRow toDepartmentDashboardRow(DepartmentStats s) {
        return new DepartmentDashboardRow(
                s.departmentId(),
                s.departmentName(),
                s.totalEmployees(),
                round1(s.avgOvertimeMinutes().doubleValue() / 60.0),
                round1((double) s.maxOvertimeMinutes() / 60.0),
                s.overtimeAlertCount(),
                s.missingClockCount(),
                round1(s.attendanceRate().doubleValue())
        );
    }

    /** 部門ダッシュボード行をソートする（インメモリ処理、部門数は少ないため問題なし） */
    private List<DepartmentDashboardRow> sortDepartmentRows(
            List<DepartmentDashboardRow> rows, String sortField, String sortDirection) {

        // デフォルトソート: 部署名昇順
        java.util.Comparator<DepartmentDashboardRow> comparator = switch (sortField != null ? sortField : "departmentName") {
            case "avgOvertimeHours" -> java.util.Comparator.comparingDouble(DepartmentDashboardRow::avgOvertimeHours);
            case "maxOvertimeHours" -> java.util.Comparator.comparingDouble(DepartmentDashboardRow::maxOvertimeHours);
            case "overtimeAlertCount" -> java.util.Comparator.comparingInt(DepartmentDashboardRow::overtimeAlertCount);
            case "missingClockCount" -> java.util.Comparator.comparingInt(DepartmentDashboardRow::missingClockCount);
            case "attendanceRate" -> java.util.Comparator.comparingDouble(DepartmentDashboardRow::attendanceRate);
            default -> java.util.Comparator.comparing(DepartmentDashboardRow::departmentName);
        };

        // ソート方向を適用する
        if ("desc".equalsIgnoreCase(sortDirection)) {
            comparator = comparator.reversed();
        }

        return rows.stream().sorted(comparator).toList();
    }

    /** リストをインメモリでページネーションする（部門数など少量データ向け） */
    private <T> PageResult<T> paginateInMemory(List<T> allItems, int page, int size) {
        long totalElements = allItems.size();
        int fromIndex = Math.min(page * size, allItems.size());
        int toIndex = Math.min(fromIndex + size, allItems.size());
        List<T> content = allItems.subList(fromIndex, toIndex);
        return PageResult.of(content, page, size, totalElements);
    }

    // ========================================
    // CSV・数値変換 ヘルパー
    // ========================================

    /** 分を時間（小数第1位）に変換する文字列（CSV用） */
    private String minutesToHours(int minutes) {
        return String.format("%.1f", (double) minutes / 60.0);
    }

    /** BigDecimalの分を時間（小数第1位）に変換する文字列（CSV用） */
    private String bigDecimalMinutesToHours(BigDecimal minutes) {
        if (minutes == null) return "0.0";
        return String.format("%.1f", minutes.doubleValue() / 60.0);
    }

    /** BigDecimalのレートを小数第1位でフォーマットする（CSV用） */
    private String formatRate(BigDecimal rate) {
        if (rate == null) return "0.0";
        return String.format("%.1f", rate.doubleValue());
    }

    /** 小数第1位で四捨五入する */
    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** CSV値をエスケープする（カンマや改行を含む場合はダブルクォートで囲む） */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** UTF-8 BOM + CSV文字列をバイト配列に変換する */
    private byte[] addBom(String csvContent) {
        byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + csvBytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(csvBytes, 0, result, UTF8_BOM.length, csvBytes.length);
        return result;
    }
}
