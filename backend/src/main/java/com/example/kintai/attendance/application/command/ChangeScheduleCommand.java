package com.example.kintai.attendance.application.command;

import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.Command;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * スケジュール変更コマンド（UC-SH-005）
 *
 * <p>既存の週次スケジュールの割当を変更するユースケースへの入力パラメータをまとめる。
 * PUBLISHEDの場合はDRAFTに戻る。</p>
 *
 * @param scheduleId     変更対象のスケジュールID
 * @param newAssignments 新しい曜日ごとの割当
 */
public record ChangeScheduleCommand(
        ScheduleId scheduleId,
        Map<DayOfWeek, ShiftPatternId> newAssignments
) implements Command {
}
