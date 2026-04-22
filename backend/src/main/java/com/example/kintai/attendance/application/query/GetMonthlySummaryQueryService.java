package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.application.query.AttendanceFinder.MonthlySummary;
import com.example.kintai.attendance.application.query.AttendanceFinder.PageResult;
import com.example.kintai.shared.kernel.contract.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 月次勤怠サマリー取得クエリサービス（UC-ATT-Q03）— KPI + 従業員別テーブルを取得する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>全従業員データを取得してKPIを集計する</li>
 *   <li>ページネーション付きで従業員別データを取得する</li>
 *   <li>分→時間に変換してMonthlySummaryResultを返却する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class GetMonthlySummaryQueryService implements QueryService<GetMonthlySummaryQuery, MonthlySummaryResult> {

    private static final Logger log = LoggerFactory.getLogger(GetMonthlySummaryQueryService.class);

    /** Read Model ファインダー */
    private final AttendanceFinder finder;

    public GetMonthlySummaryQueryService(AttendanceFinder finder) {
        this.finder = finder;
    }

    /**
     * 月次勤怠サマリーを取得する
     *
     * @param query 月次サマリー取得クエリ（部門ID、年月、ページネーション）
     * @return KPI + 従業員別テーブルの結果
     */
    @Override
    public MonthlySummaryResult execute(GetMonthlySummaryQuery query) {
        String sort = query.sortField() != null ? query.sortField() : "employeeName";
        String dir = query.sortDirection() != null ? query.sortDirection() : "asc";

        log.debug("月次サマリー: departmentId={}, year={}, month={}, page={}, size={}",
                query.departmentId(), query.year(), query.month(), query.page(), query.size());

        // 全従業員データを取得してKPIを集計する（ページネーション前の全データ）
        List<MonthlySummary> allSummaries =
                finder.findMonthlySummariesByDepartment(query.departmentId(), query.year(), query.month());
        MonthlySummaryKpi kpi = aggregateMonthlySummaryKpi(allSummaries);

        // ページネーション付きで従業員別データを取得する
        PageResult<MonthlySummary> pagedRaw =
                finder.findMonthlySummariesByDepartmentPaged(
                        query.departmentId(), query.year(), query.month(), query.page(), query.size(), sort, dir);

        // 分→時間に変換した行データを作成する
        List<MonthlySummaryRow> rows = pagedRaw.content().stream()
                .map(this::toMonthlySummaryRow)
                .toList();
        PageResult<MonthlySummaryRow> pagedRows = PageResult.of(
                rows, pagedRaw.page(), pagedRaw.size(), pagedRaw.totalElements());

        return new MonthlySummaryResult(kpi, pagedRows);
    }

    /** 月次サマリーからKPI（4枚のカード）を集計する */
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

    /** 小数第1位で四捨五入する */
    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
