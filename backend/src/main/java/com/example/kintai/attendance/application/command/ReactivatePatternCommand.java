package com.example.kintai.attendance.application.command;

import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.Command;

/**
 * シフトパターン再有効化コマンド（UC-SH-003）
 *
 * <p>無効化されたシフトパターンを再有効化するユースケースへの入力パラメータをまとめる。
 * INACTIVEのパターンをACTIVEに戻す。</p>
 *
 * @param patternId 再有効化するシフトパターンID
 */
public record ReactivatePatternCommand(
        ShiftPatternId patternId
) implements Command {
}
