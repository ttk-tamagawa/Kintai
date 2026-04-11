package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.ClockSource;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.kernel.contract.Command;

/**
 * 休憩終了コマンド（UC-ATT-004）
 *
 * <p>休憩終了ユースケースへの入力パラメータをまとめる。
 * 休憩時間は集約内で自動計算される。</p>
 *
 * @param id        勤怠記録ID
 * @param clockTime 休憩終了時刻
 * @param source    打刻元（WEB/MOBILE）
 */
public record EndBreakCommand(
        AttendanceRecordId id,
        ClockTime clockTime,
        ClockSource source
) implements Command {
}
