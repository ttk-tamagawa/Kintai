package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.ManualAttendance;
import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.Command;

/**
 * 勤務実績登録コマンド（UC-ATT-006）
 *
 * <p>承認済みの手動勤務実績登録ユースケースへの入力パラメータをまとめる。
 * 出勤・退勤を一括登録し、勤務時間が自動計算される。</p>
 *
 * @param employeeId     従業員ID
 * @param workDate       勤務日
 * @param manual         手動勤務登録内容（承認済み）
 * @param shiftPatternId シフトパターンID（nullable: 固定時間制の場合はnull）
 */
public record RegisterManualAttendanceCommand(
        EmployeeId employeeId,
        WorkDate workDate,
        ManualAttendance manual,
        ShiftPatternId shiftPatternId
) implements Command {
}
