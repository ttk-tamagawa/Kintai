package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository;
import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository.DailySummary;
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
 *   <li>当日の日次サマリーをRead Modelから取得する</li>
 *   <li>Write Modelから休憩中フラグを判定する</li>
 *   <li>TodayAttendanceResultに変換して返却する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class GetTodayAttendanceQueryService implements QueryService<GetTodayAttendanceQuery, Optional<TodayAttendanceResult>> {

    private static final Logger log = LoggerFactory.getLogger(GetTodayAttendanceQueryService.class);

    /** Read Modelクエリリポジトリ */
    private final AttendanceSummaryQueryRepository queryRepository;

    /** Write Modelリポジトリ（休憩中フラグの判定に使用） */
    private final AttendanceRecordRepository attendanceRecordRepository;

    public GetTodayAttendanceQueryService(
            AttendanceSummaryQueryRepository queryRepository,
            AttendanceRecordRepository attendanceRecordRepository
    ) {
        this.queryRepository = queryRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
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
        List<DailySummary> summaries = queryRepository.findDailySummaries(query.employeeId(), today, today);

        // 当日分がない場合は空を返す
        if (summaries.isEmpty()) {
            return Optional.empty();
        }

        // DailySummary → TodayAttendanceResult に変換する
        DailySummary s = summaries.getFirst();

        // 書き込みモデルから休憩中フラグを取得する（打刻エントリの BREAK_START/END 数で判定）
        boolean onBreak = attendanceRecordRepository
                .findByEmployeeIdAndWorkDate(query.employeeId(), new WorkDate(today))
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
}
