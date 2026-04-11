package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.model.shift.WeeklySchedule;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleRepository;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;

/**
 * スケジュール変更ユースケース（UC-SH-005）— 既存の週次スケジュールの割当を変更する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>スケジュールをリポジトリから取得する（存在しなければ例外）</li>
 *   <li>新しい割当パターンが全てACTIVEであることを検証する（INV-SH-002）</li>
 *   <li>集約のchangeAssignments()を呼び出す（PUBLISHEDならDRAFTに戻る）</li>
 *   <li>リポジトリに保存し、ドメインイベントを一括で永続化・発行する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional
public class ChangeScheduleUseCase implements UseCase<ChangeScheduleCommand, AssignScheduleResult> {

    private static final Logger log = LoggerFactory.getLogger(ChangeScheduleUseCase.class);

    /** 週次スケジュールリポジトリ — スケジュールの保存用 */
    private final WeeklyScheduleRepository weeklyScheduleRepository;

    /** シフトパターンリポジトリ — パターンのACTIVE検証用 */
    private final ShiftPatternRepository shiftPatternRepository;

    /** 共通サポート — スケジュール取得・イベント永続化用 */
    private final WeeklyScheduleUseCaseSupport support;

    public ChangeScheduleUseCase(
            WeeklyScheduleRepository weeklyScheduleRepository,
            ShiftPatternRepository shiftPatternRepository,
            WeeklyScheduleUseCaseSupport support
    ) {
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.shiftPatternRepository = shiftPatternRepository;
        this.support = support;
    }

    /**
     * スケジュール変更を実行する
     *
     * @param command スケジュール変更コマンド（スケジュールID、新しい曜日ごとの割当）
     * @return 変更結果（スケジュールとパターン名を含む）
     * @throws IllegalArgumentException スケジュールが見つからない場合、パターンがINACTIVEの場合
     */
    @Override
    public AssignScheduleResult execute(ChangeScheduleCommand command) {
        log.debug("スケジュール変更: scheduleId={}", command.scheduleId().value());

        // スケジュールをリポジトリから取得する（存在しなければ例外）
        WeeklySchedule schedule = support.findScheduleOrThrow(command.scheduleId());

        // 新しい割当パターンが全てACTIVEであることを検証し、パターン名を取得する（INV-SH-002）
        Map<ShiftPatternId, String> patternNames = validateAndCollectPatternNames(command.newAssignments());

        // 集約のchangeAssignments()を呼び出す（PUBLISHEDならDRAFTに戻る、変更前ステータスは集約内で退避）
        schedule.changeAssignments(command.newAssignments());

        // リポジトリに保存する（楽観的ロックでバージョン管理）
        weeklyScheduleRepository.save(schedule);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        support.publishAndPersistEvents(schedule);

        log.debug("スケジュール変更完了: scheduleId={}, newStatus={}",
                command.scheduleId().value(), schedule.getStatus());
        return new AssignScheduleResult(schedule, patternNames);
    }

    /**
     * 割当パターンが全てACTIVEであることを検証し、パターン名を収集する
     *
     * <p>INV-SH-002: 非アクティブなパターンは新規割当に使用不可。</p>
     *
     * @param assignments 曜日ごとのパターン割当
     * @return パターンID→パターン名のマップ
     * @throws IllegalArgumentException パターンが見つからない場合、INACTIVEの場合
     */
    private Map<ShiftPatternId, String> validateAndCollectPatternNames(
            Map<DayOfWeek, ShiftPatternId> assignments) {
        Map<ShiftPatternId, String> patternNames = new HashMap<>();

        for (Map.Entry<DayOfWeek, ShiftPatternId> entry : assignments.entrySet()) {
            ShiftPatternId patternId = entry.getValue();

            // パターンをリポジトリから取得する（存在しなければ例外）
            ShiftPattern pattern = shiftPatternRepository.findById(patternId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "シフトパターンが見つかりません: " + patternId.value()
                                    + "（" + entry.getKey() + "の割当）"
                    ));

            // ACTIVEであることを検証する（INV-SH-002）
            if (!pattern.isActive()) {
                throw new IllegalArgumentException(
                        "パターン「" + pattern.getName().value() + "」は無効化されているため割当できません"
                                + "（" + entry.getKey() + "の割当）"
                );
            }

            // パターン名を収集する（レスポンス構築用）
            patternNames.put(patternId, pattern.getName().value());
        }

        return patternNames;
    }
}
