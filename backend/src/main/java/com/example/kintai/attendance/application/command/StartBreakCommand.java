package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.ClockSource;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.kernel.contract.Command;

/**
 * 休憩開始コマンド（UC-ATT-003）
 *
 * <p>休憩開始ユースケースへの入力パラメータをまとめる。
 * ステータスはCLOCKED_INのまま変化しない。</p>
 *
 * @param id        勤怠記録ID
 * @param clockTime 休憩開始時刻
 * @param source    打刻元（WEB/MOBILE）
 */
public record StartBreakCommand(
        AttendanceRecordId id,
        ClockTime clockTime,
        ClockSource source
) implements Command {
}
