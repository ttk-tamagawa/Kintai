package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.application.query.AttendanceFinder.DepartmentStats;
import com.example.kintai.attendance.application.query.AttendanceFinder.PageResult;
import com.example.kintai.shared.kernel.contract.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * 部門別勤怠ダッシュボード取得クエリサービス（UC-ATT-Q05）— KPI + 前月KPI + 部門別テーブルを取得する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>当月の部門統計を取得し、部門IDフィルタを適用する</li>
 *   <li>当月のKPIを集計する</li>
 *   <li>前月の統計を取得してKPIを集計する（前月比トレンド用）</li>
 *   <li>ソート・ページネーションを適用して返却する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class GetDepartmentDashboardQueryService implements QueryService<GetDepartmentDashboardQuery, DepartmentDashboardResult> {

    private static final Logger log = LoggerFactory.getLogger(GetDepartmentDashboardQueryService.class);

    /** Read Model ファインダー */
    private final AttendanceFinder finder;

    public GetDepartmentDashboardQueryService(AttendanceFinder finder) {
        this.finder = finder;
    }

    /**
     * 部門別勤怠ダッシュボードを取得する
     *
     * @param query 部門ダッシュボード取得クエリ（部門ID、年月、ページネーション）
     * @return KPI + 前月KPI + 部門別テーブルの結果
     */
    @Override
    public DepartmentDashboardResult execute(GetDepartmentDashboardQuery query) {
        log.debug("部門ダッシュボード: departmentId={}, year={}, month={}, page={}, size={}",
                query.departmentId(), query.year(), query.month(), query.page(), query.size());

        // 当月の部門統計を取得する
        List<DepartmentStats> currentStats = finder.findDepartmentStats(query.year(), query.month());

        // 部門IDフィルタが指定されている場合は絞り込む
        if (query.departmentId() != null && !query.departmentId().isEmpty()) {
            currentStats = currentStats.stream()
                    .filter(s -> query.departmentId().equals(s.departmentId()))
                    .toList();
        }

        // 当月のKPIを集計する
        DepartmentDashboardKpi currentKpi = aggregateDepartmentKpi(currentStats);

        // 前月の部門統計を取得してKPIを集計する
        YearMonth prevMonth = YearMonth.of(query.year(), query.month()).minusMonths(1);
        List<DepartmentStats> prevStats = finder.findDepartmentStats(
                prevMonth.getYear(), prevMonth.getMonthValue());
        if (query.departmentId() != null && !query.departmentId().isEmpty()) {
            prevStats = prevStats.stream()
                    .filter(s -> query.departmentId().equals(s.departmentId()))
                    .toList();
        }
        DepartmentDashboardKpi previousKpi = aggregateDepartmentKpi(prevStats);

        // ソートを適用してページネーションする（部門数は少ないためインメモリ処理）
        List<DepartmentDashboardRow> allRows = currentStats.stream()
                .map(this::toDepartmentDashboardRow)
                .toList();
        List<DepartmentDashboardRow> sortedRows = sortDepartmentRows(
                allRows, query.sortField(), query.sortDirection());
        PageResult<DepartmentDashboardRow> pagedRows = paginateInMemory(
                sortedRows, query.page(), query.size());

        return new DepartmentDashboardResult(currentKpi, previousKpi, pagedRows);
    }

    /** 部門統計からダッシュボードKPI（4枚のカード）を集計する */
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
        Comparator<DepartmentDashboardRow> comparator = switch (sortField != null ? sortField : "departmentName") {
            case "avgOvertimeHours" -> Comparator.comparingDouble(DepartmentDashboardRow::avgOvertimeHours);
            case "maxOvertimeHours" -> Comparator.comparingDouble(DepartmentDashboardRow::maxOvertimeHours);
            case "overtimeAlertCount" -> Comparator.comparingInt(DepartmentDashboardRow::overtimeAlertCount);
            case "missingClockCount" -> Comparator.comparingInt(DepartmentDashboardRow::missingClockCount);
            case "attendanceRate" -> Comparator.comparingDouble(DepartmentDashboardRow::attendanceRate);
            default -> Comparator.comparing(DepartmentDashboardRow::departmentName);
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

    /** 小数第1位で四捨五入する */
    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
