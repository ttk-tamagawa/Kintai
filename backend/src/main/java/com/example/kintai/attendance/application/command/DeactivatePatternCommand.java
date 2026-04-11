package com.example.kintai.attendance.application.command;

import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.Command;

/**
 * シフトパターン無効化コマンド（UC-SH-002）
 *
 * <p>既存のシフトパターンを無効化するユースケースへの入力パラメータをまとめる。
 * 未来の週次スケジュールで使用されていないことが前提条件。</p>
 *
 * @param patternId 無効化するシフトパターンID
 */
public record DeactivatePatternCommand(
        ShiftPatternId patternId
) implements Command {
}
