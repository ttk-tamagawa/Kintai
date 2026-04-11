package com.example.kintai.attendance.application.command;

import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.kernel.contract.Command;

/**
 * スケジュール非公開コマンド（UC-SH-007）
 *
 * <p>公開済みの週次スケジュールを非公開にするユースケースへの入力パラメータをまとめる。
 * PUBLISHEDからDRAFTに遷移させる。</p>
 *
 * @param scheduleId 非公開対象のスケジュールID
 */
public record UnpublishScheduleCommand(
        ScheduleId scheduleId
) implements Command {
}
