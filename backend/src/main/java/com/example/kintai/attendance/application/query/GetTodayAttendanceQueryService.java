package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.application.query.AttendanceFinder.DailySummary;
import com.example.kintai.shared.kernel.contract.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 当日勤怠ステータス取得クエリサービス（UC-ATT-Q01）— 勤怠打刻モーダルの初期表示データを取得する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>当日の日次サマリーを Read Model から取得する</li>
 *   <li>Read Model の {@code onBreak} フラグを含めて TodayAttendanceResult に変換して返却する</li>
 * </ol>
 * </p>
 *
 * <p>Read Model だけで完結するため、Write Model（AttendanceRecord 集約）へのアクセスは一切行わない。
 * review-009 指摘 #3 対応: 休憩中判定のために clock_entries 全件をロードしていた無駄なコストを排除。</p>
 */
@Service
@Transactional(readOnly = true)
public class GetTodayAttendanceQueryService implements QueryService<GetTodayAttendanceQuery, Optional<TodayAttendanceResult>> {

    private static final Logger log = LoggerFactory.getLogger(GetTodayAttendanceQueryService.class);

    /** Read Model ファインダー */
    private final AttendanceFinder finder;

    public GetTodayAttendanceQueryService(AttendanceFinder finder) {
        this.finder = finder;
    }

    /**
     * 当日の勤怠ステータスを取得する
     *
     * @param query 当日勤怠取得クエリ（従業員ID）
     * @return 当日の勤怠ステータス（存在しない場合はempty）
     */
    @Override
    public Optional<TodayAttendanceResult> execute(GetTodayAttendanceQuery query) {
        LocalDate today = LocalDate.now();
        log.debug("当日勤怠取得: employeeId={}, date={}", query.employeeId().value(), today);

        // 当日の日次サマリーを取得する（from=to=today で1件取得）
        List<DailySummary> summaries = finder.findDailySummaries(query.employeeId(), today, today);

        // 当日分がない場合は空を返す
        if (summaries.isEmpty()) {
            return Optional.empty();
        }

        // DailySummary → TodayAttendanceResult に変換する（休憩中フラグも Read Model から取得）
        DailySummary s = summaries.getFirst();

        return Optional.of(new TodayAttendanceResult(
                s.attendanceId(),
                s.employeeId(),
                s.workDate(),
                s.status(),
                s.onBreak(),
                s.clockInTime(),
                s.clockOutTime(),
                s.breakMinutes(),
                // 未退勤の場合はnull（計算未実施）
                s.netWorkMinutes() > 0 ? s.netWorkMinutes() : null,
                s.totalOvertimeMinutes() > 0 ? s.totalOvertimeMinutes() : null,
                s.updatedAt()
        ));
    }
}
