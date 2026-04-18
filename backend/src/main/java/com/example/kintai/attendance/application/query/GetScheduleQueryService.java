package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.domain.repository.ShiftQueryRepository;
import com.example.kintai.attendance.domain.repository.ShiftQueryRepository.ScheduleSummary;
import com.example.kintai.shared.domain.exception.ResourceNotFoundException;
import com.example.kintai.shared.kernel.contract.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 週次スケジュール詳細取得クエリサービス（UC-SH-Q04）— スケジュールの詳細を取得する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>スケジュールIDでShiftQueryRepositoryから検索する</li>
 *   <li>見つからない場合は例外をスローする</li>
 * </ol>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class GetScheduleQueryService implements QueryService<GetScheduleQuery, ScheduleSummary> {

    private static final Logger log = LoggerFactory.getLogger(GetScheduleQueryService.class);

    /** シフトクエリリポジトリ — Read Model参照用 */
    private final ShiftQueryRepository shiftQueryRepository;

    public GetScheduleQueryService(ShiftQueryRepository shiftQueryRepository) {
        this.shiftQueryRepository = shiftQueryRepository;
    }

    /**
     * スケジュールの詳細を取得する
     *
     * @param query スケジュール詳細取得クエリ（スケジュールID）
     * @return スケジュール概要
     * @throws ResourceNotFoundException スケジュールが見つからない場合
     */
    @Override
    public ScheduleSummary execute(GetScheduleQuery query) {
        log.debug("スケジュール詳細取得: scheduleId={}", query.scheduleId());

        // ShiftQueryRepositoryからIDで検索する（見つからなければ例外）
        ScheduleSummary schedule = shiftQueryRepository.findScheduleById(query.scheduleId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "スケジュールが見つかりません: " + query.scheduleId()));

        log.debug("スケジュール詳細取得完了: employeeId={}, weekStartDate={}",
                schedule.employeeId(), schedule.weekStartDate());
        return schedule;
    }
}
