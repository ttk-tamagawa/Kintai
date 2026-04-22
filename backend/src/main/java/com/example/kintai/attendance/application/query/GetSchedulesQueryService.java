package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.application.query.ShiftFinder.ScheduleSummary;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.kernel.contract.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * 週次スケジュール一覧取得クエリサービス（UC-SH-Q03）— 従業員と期間でスケジュール一覧を取得する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>from/toが省略された場合はデフォルト値を設定する（今週の月曜〜4週先の日曜）</li>
 *   <li>employeeIdが省略された場合は全従業員のスケジュールを返す（カレンダー表示用）</li>
 *   <li>ShiftFinderからRead Modelを参照してスケジュール一覧を取得する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class GetSchedulesQueryService implements QueryService<GetSchedulesQuery, List<ScheduleSummary>> {

    private static final Logger log = LoggerFactory.getLogger(GetSchedulesQueryService.class);

    /** デフォルトの表示週数 — 今週を含む4週先まで */
    private static final int DEFAULT_WEEKS_AHEAD = 4;

    /** シフトファインダー — Read Model参照用 */
    private final ShiftFinder shiftFinder;

    public GetSchedulesQueryService(ShiftFinder shiftFinder) {
        this.shiftFinder = shiftFinder;
    }

    /**
     * 週次スケジュール一覧を取得する
     *
     * @param query スケジュール一覧取得クエリ（従業員ID、期間）
     * @return スケジュール概要のリスト
     */
    @Override
    public List<ScheduleSummary> execute(GetSchedulesQuery query) {
        log.debug("スケジュール一覧取得: employeeId={}, from={}, to={}",
                query.employeeId(), query.from(), query.to());

        // from/toが省略された場合はデフォルト値を設定する
        LocalDate effectiveFrom = (query.from() != null) ? query.from() : getThisMonday();
        LocalDate effectiveTo = (query.to() != null) ? query.to() : effectiveFrom.plusWeeks(DEFAULT_WEEKS_AHEAD)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));

        // employeeIdが省略された場合は全従業員のスケジュールを返す
        List<ScheduleSummary> schedules;
        if (query.employeeId() != null) {
            schedules = shiftFinder.findSchedules(
                    EmployeeId.of(query.employeeId()), effectiveFrom, effectiveTo
            );
        } else {
            schedules = shiftFinder.findAllSchedules(effectiveFrom, effectiveTo);
        }

        log.debug("スケジュール一覧取得完了: {}件", schedules.size());
        return schedules;
    }

    /** 今週の月曜日を取得する */
    private LocalDate getThisMonday() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
