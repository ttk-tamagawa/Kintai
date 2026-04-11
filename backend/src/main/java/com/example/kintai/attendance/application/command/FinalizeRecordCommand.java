package com.example.kintai.attendance.application.command;

import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.MonthlyClosingId;
import com.example.kintai.shared.kernel.contract.Command;

/**
 * 本締め確定コマンド（UC-ATT-007）
 *
 * <p>月次本締めによる勤怠記録確定ユースケースへの入力パラメータをまとめる。
 * ステータスがCLOCKED_OUT→FINALIZEDに遷移する。</p>
 *
 * @param id               勤怠記録ID
 * @param monthlyClosingId 月次締めID（どの締め処理に基づく確定か）
 */
public record FinalizeRecordCommand(
        AttendanceRecordId id,
        MonthlyClosingId monthlyClosingId
) implements Command {
}
