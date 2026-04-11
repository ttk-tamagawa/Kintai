package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.WeeklySchedule;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleRepository;
import com.example.kintai.shared.kernel.contract.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * スケジュール非公開ユースケース（UC-SH-007）— 公開済みの週次スケジュールを非公開にする
 *
 * <p>処理フロー:
 * <ol>
 *   <li>スケジュールをリポジトリから取得する（存在しなければ例外）</li>
 *   <li>集約のunpublish()を呼び出す（ガード: PUBLISHEDであること）</li>
 *   <li>リポジトリに保存する</li>
 *   <li>UNPUBLISHEDイベントをイベントストアに追記し、Springイベントを発行する</li>
 * </ol>
 * </p>
 */
@Service
@Transactional
public class UnpublishScheduleUseCase implements UseCase<UnpublishScheduleCommand, WeeklySchedule> {

    private static final Logger log = LoggerFactory.getLogger(UnpublishScheduleUseCase.class);

    /** 週次スケジュールリポジトリ — スケジュールの保存用 */
    private final WeeklyScheduleRepository weeklyScheduleRepository;

    /** 共通サポート — スケジュール取得・イベント永続化用 */
    private final WeeklyScheduleUseCaseSupport support;

    public UnpublishScheduleUseCase(
            WeeklyScheduleRepository weeklyScheduleRepository,
            WeeklyScheduleUseCaseSupport support
    ) {
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.support = support;
    }

    /**
     * スケジュール非公開を実行する
     *
     * @param command スケジュール非公開コマンド（スケジュールID）
     * @return 非公開後のスケジュール（Write Model）
     * @throws IllegalArgumentException スケジュールが見つからない場合
     * @throws IllegalStateException    PUBLISHEDでない場合
     */
    @Override
    public WeeklySchedule execute(UnpublishScheduleCommand command) {
        log.debug("スケジュール非公開: scheduleId={}", command.scheduleId().value());

        // スケジュールをリポジトリから取得する（存在しなければ例外）
        WeeklySchedule schedule = support.findScheduleOrThrow(command.scheduleId());

        // 集約のunpublish()を呼び出す（ガード: PUBLISHEDであること → DRAFT に遷移）
        schedule.unpublish();

        // リポジトリに保存する（楽観的ロックでバージョン管理）
        weeklyScheduleRepository.save(schedule);

        // 集約に蓄積されたドメインイベントを一括で永続化・発行する
        support.publishAndPersistEvents(schedule);

        log.debug("スケジュール非公開完了: scheduleId={}", command.scheduleId().value());
        return schedule;
    }
}
