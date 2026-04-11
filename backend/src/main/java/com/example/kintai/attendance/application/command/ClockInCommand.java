package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.ClockSource;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.Command;

/**
 * 出勤打刻コマンド（UC-ATT-001）
 *
 * <p>出勤打刻ユースケースへの入力パラメータをまとめる。
 * 打刻時刻から勤務日を算出し、既存レコードがなければ新規作成する。</p>
 *
 * @param employeeId     従業員ID
 * @param clockTime      出勤打刻時刻
 * @param source         打刻元（WEB/MOBILE）
 * @param shiftPatternId シフトパターンID（nullable: 固定時間制の場合はnull）
 */
public record ClockInCommand(
        EmployeeId employeeId,
        ClockTime clockTime,
        ClockSource source,
        ShiftPatternId shiftPatternId
) implements Command {
}
