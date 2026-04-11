package com.example.kintai.attendance.application.command;

import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.Command;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * スケジュール割当コマンド（UC-SH-004）
 *
 * <p>新しい週次スケジュールを割り当てるユースケースへの入力パラメータをまとめる。
 * 1従業員1週1スケジュールの一意性制約あり。DRAFT状態で作成される。</p>
 *
 * @param employeeId    従業員ID
 * @param weekStartDate 週の開始日（月曜日）
 * @param assignments   曜日ごとのシフトパターン割当
 */
public record AssignScheduleCommand(
        EmployeeId employeeId,
        LocalDate weekStartDate,
        Map<DayOfWeek, ShiftPatternId> assignments
) implements Command {
}
