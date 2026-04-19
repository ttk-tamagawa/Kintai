package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.WeeklySchedule;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleRepository;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** 共通サポート — スケジュール取得・パターン検証・イベント永続化用 */
    private final WeeklyScheduleUseCaseSupport support;

    public ChangeScheduleUseCase(
            WeeklyScheduleRepository weeklyScheduleRepository,
            WeeklyScheduleUseCaseSupport support
    ) {
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.support = support;
    }

    /**
     * スケジュール変更を実行する
     *
     * @param command スケジュール変更コマンド（スケジュールID、新しい曜日ごとの割当）
     * @return 変更結果（スケジュールとパターン名を含む）
     */
    @Override
    public AssignScheduleResult execute(ChangeScheduleCommand command) {
        log.debug("スケジュール変更: scheduleId={}", command.scheduleId().value());

        // スケジュールをリポジトリから取得する（存在しなければ例外）
        WeeklySchedule schedule = support.findScheduleOrThrow(command.scheduleId());

        // 新しい割当パターンが全てACTIVEであることを検証し、パターン名を取得する（INV-SH-002）
        // 内部でfindAllByIdによる一括取得を行うためN+1が発生しない
        Map<ShiftPatternId, String> patternNames = support.validateAndCollectPatternNames(command.newAssignments());

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
}
