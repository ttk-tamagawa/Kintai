package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.WeeklySchedule;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleRepository;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * スケジュール公開ユースケース（UC-SH-006）— 週次スケジュールを公開する
 *
 * <p>処理フロー:
 * <ol>
 *   <li>スケジュールをリポジトリから取得する（存在しなければ例外）</li>
 *   <li>集約のpublish()を呼び出す（ガード: DRAFTであること）</li>
 *   <li>リポジトリに保存する</li>
 *   <li>PUBLISHEDイベントをイベントストアに追記し、Springイベントを発行する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional
public class PublishScheduleUseCase implements UseCase<PublishScheduleCommand, WeeklySchedule> {

    private static final Logger log = LoggerFactory.getLogger(PublishScheduleUseCase.class);

    /** 週次スケジュールリポジトリ — スケジュールの保存用 */
    private final WeeklyScheduleRepository weeklyScheduleRepository;

    /** 共通サポート — スケジュール取得・イベント永続化用 */
    private final WeeklyScheduleUseCaseSupport support;

    public PublishScheduleUseCase(
            WeeklyScheduleRepository weeklyScheduleRepository,
            WeeklyScheduleUseCaseSupport support
    ) {
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.support = support;
    }

    /**
     * スケジュール公開を実行する
     *
     * @param command スケジュール公開コマンド（スケジュールID）
     * @return 公開後のスケジュール（Write Model）
     */
    @Override
    public WeeklySchedule execute(PublishScheduleCommand command) {
        log.debug("スケジュール公開: scheduleId={}", command.scheduleId().value());

        // スケジュールをリポジトリから取得する（存在しなければ例外）
        WeeklySchedule schedule = support.findScheduleOrThrow(command.scheduleId());

        // 集約のpublish()を呼び出す（ガード: DRAFTであること → PUBLISHED に遷移）
        schedule.publish();

        // リポジトリに保存する（楽観的ロックでバージョン管理）
        weeklyScheduleRepository.save(schedule);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        support.publishAndPersistEvents(schedule);

        log.debug("スケジュール公開完了: scheduleId={}", command.scheduleId().value());
        return schedule;
    }
}
