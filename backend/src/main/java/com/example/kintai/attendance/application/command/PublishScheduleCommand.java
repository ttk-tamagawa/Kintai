package com.example.kintai.attendance.application.command;

import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.kernel.contract.Command;

/**
 * スケジュール公開コマンド（UC-SH-006）
 *
 * <p>週次スケジュールを公開するユースケースへの入力パラメータをまとめる。
 * DRAFTからPUBLISHEDに遷移させる。</p>
 *
 * @param scheduleId 公開対象のスケジュールID
 */
public record PublishScheduleCommand(
        ScheduleId scheduleId
) implements Command {
}
