package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.ClockCorrection;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.kernel.contract.Command;

/**
 * 打刻修正コマンド（UC-ATT-005）
 *
 * <p>承認済みの打刻修正ユースケースへの入力パラメータをまとめる。
 * 退勤済みの場合は勤務時間が再計算される。</p>
 *
 * @param id         勤怠記録ID
 * @param correction 打刻修正内容（承認済み）
 */
public record CorrectClockCommand(
        AttendanceRecordId id,
        ClockCorrection correction
) implements Command {
}
