package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.domain.repository.ShiftQueryRepository;
import com.example.kintai.attendance.domain.repository.ShiftQueryRepository.ScheduleSummary;
import com.example.kintai.shared.domain.model.EmployeeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

/**
 * 週次スケジュールクエリサービス — 週次スケジュールの2つの読み取りユースケースを統合するアプリケーションサービス
 *
 * <p>CQRS（コマンドクエリ責務分離）の読み取り側。
 * ShiftQueryRepository（Read Model参照用）を使用して週次スケジュールのデータを取得する。
 * Read Modelはweekly_schedule_summariesテーブルで、パターン名が非正規化されている。</p>
 *
 * <p>対応ユースケース:
 * <ul>
 *   <li>UC-SH-Q03: 週次スケジュール一覧を取得する（getSchedules）</li>
 *   <li>UC-SH-Q04: 週次スケジュール詳細を取得する（getSchedule）</li>
 * </ul>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class WeeklyScheduleQueryService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyScheduleQueryService.class);

    /** デフォルトの表示週数 — 今週を含む4週先まで */
    private static final int DEFAULT_WEEKS_AHEAD = 4;

    /** シフトクエリリポジトリ — Read Model参照用 */
    private final ShiftQueryRepository shiftQueryRepository;

    /**
     * コンストラクタ — クエリリポジトリを注入する
     */
    public WeeklyScheduleQueryService(ShiftQueryRepository shiftQueryRepository) {
        this.shiftQueryRepository = shiftQueryRepository;
    }

    // ========================================
    // UC-SH-Q03: 週次スケジュール一覧を取得する
    // ========================================

    /**
     * 従業員と期間で週次スケジュール一覧を取得する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>from/toが省略された場合はデフォルト値を設定する（今週の月曜〜4週先の日曜）</li>
     *   <li>employeeIdが省略された場合は全従業員のスケジュールを返す（カレンダー表示用）</li>
     *   <li>ShiftQueryRepositoryからRead Modelを参照してスケジュール一覧を取得する</li>
     * </ol>
     * </p>
     *
     * @param employeeId 従業員ID（nullの場合は全従業員分を返却）
     * @param from       検索開始日（nullの場合は今週の月曜日）
     * @param to         検索終了日（nullの場合はfromから4週先の日曜日）
     * @return スケジュール概要のリスト
     */
    public List<ScheduleSummary> getSchedules(UUID employeeId, LocalDate from, LocalDate to) {
        log.debug("スケジュール一覧取得: employeeId={}, from={}, to={}", employeeId, from, to);

        // from/toが省略された場合はデフォルト値を設定する
        LocalDate effectiveFrom = (from != null) ? from : getThisMonday();
        LocalDate effectiveTo = (to != null) ? to : effectiveFrom.plusWeeks(DEFAULT_WEEKS_AHEAD)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));

        // employeeIdが省略された場合は全従業員のスケジュールを返す
        List<ScheduleSummary> schedules;
        if (employeeId != null) {
            schedules = shiftQueryRepository.findSchedules(
                    EmployeeId.of(employeeId), effectiveFrom, effectiveTo
            );
        } else {
            schedules = shiftQueryRepository.findAllSchedules(effectiveFrom, effectiveTo);
        }

        log.debug("スケジュール一覧取得完了: {}件", schedules.size());
        return schedules;
    }

    // ========================================
    // UC-SH-Q04: 週次スケジュール詳細を取得する
    // ========================================

    /**
     * スケジュールの詳細を取得する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>スケジュールIDでShiftQueryRepositoryから検索する</li>
     *   <li>見つからない場合は例外をスローする</li>
     * </ol>
     * </p>
     *
     * @param scheduleId スケジュールID
     * @return スケジュール概要
     * @throws IllegalArgumentException スケジュールが見つからない場合
     */
    public ScheduleSummary getSchedule(UUID scheduleId) {
        log.debug("スケジュール詳細取得: scheduleId={}", scheduleId);

        // ShiftQueryRepositoryからIDで検索する（見つからなければ例外）
        ScheduleSummary schedule = shiftQueryRepository.findScheduleById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "スケジュールが見つかりません: " + scheduleId));

        log.debug("スケジュール詳細取得完了: employeeId={}, weekStartDate={}",
                schedule.employeeId(), schedule.weekStartDate());
        return schedule;
    }

    // ========================================
    // ヘルパーメソッド
    // ========================================

    /**
     * 今週の月曜日を取得する
     *
     * @return 今週の月曜日の日付
     */
    private LocalDate getThisMonday() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
