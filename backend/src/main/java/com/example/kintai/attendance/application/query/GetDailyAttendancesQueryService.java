package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository;
import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository.DailySummary;
import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository.PageResult;
import com.example.kintai.shared.kernel.contract.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 日次勤怠一覧取得クエリサービス（UC-ATT-Q02）— 日次勤怠一覧をページネーション付きで取得する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>デフォルト値を設定する（nullの場合は当月範囲）</li>
 *   <li>ページネーション付きで日次サマリーをRead Modelから取得する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class GetDailyAttendancesQueryService implements QueryService<GetDailyAttendancesQuery, PageResult<DailySummary>> {

    private static final Logger log = LoggerFactory.getLogger(GetDailyAttendancesQueryService.class);

    /** Read Modelクエリリポジトリ */
    private final AttendanceSummaryQueryRepository queryRepository;

    public GetDailyAttendancesQueryService(AttendanceSummaryQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    /**
     * 日次勤怠一覧をページネーション付きで取得する
     *
     * @param query 日次勤怠一覧取得クエリ（従業員ID、期間、フィルタ、ページネーション）
     * @return ページネーション結果
     */
    @Override
    public PageResult<DailySummary> execute(GetDailyAttendancesQuery query) {
        // デフォルト値を設定する（nullの場合は当月範囲）
        YearMonth currentMonth = YearMonth.now();
        LocalDate from = query.dateFrom() != null ? query.dateFrom() : currentMonth.atDay(1);
        LocalDate to = query.dateTo() != null ? query.dateTo() : currentMonth.atEndOfMonth();
        String sort = query.sortField() != null ? query.sortField() : "workDate";
        String dir = query.sortDirection() != null ? query.sortDirection() : "desc";

        log.debug("日次勤怠一覧: employeeId={}, from={}, to={}, status={}, page={}, size={}",
                query.employeeId().value(), from, to, query.status(), query.page(), query.size());

        // ページネーション付きで日次サマリーを取得する
        return queryRepository.findDailySummariesPaged(
                query.employeeId(), from, to, query.status(), query.page(), query.size(), sort, dir);
    }
}
