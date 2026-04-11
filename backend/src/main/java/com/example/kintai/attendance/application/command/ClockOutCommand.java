package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.ClockSource;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.kernel.contract.Command;

/**
 * 退勤打刻コマンド（UC-ATT-002）
 *
 * <p>退勤打刻ユースケースへの入力パラメータをまとめる。
 * 退勤後に勤務時間が自動計算される。</p>
 *
 * @param id        勤怠記録ID
 * @param clockTime 退勤打刻時刻
 * @param source    打刻元（WEB/MOBILE）
 */
public record ClockOutCommand(
        AttendanceRecordId id,
        ClockTime clockTime,
        ClockSource source
) implements Command {
}
