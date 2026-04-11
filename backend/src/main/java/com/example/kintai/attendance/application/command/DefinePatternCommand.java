package com.example.kintai.attendance.application.command;

import com.example.kintai.shared.kernel.contract.Command;

import java.time.LocalTime;

/**
 * シフトパターン定義コマンド（UC-SH-001）
 *
 * <p>新しいシフトパターンを定義するユースケースへの入力パラメータをまとめる。
 * パターン名の一意性チェック後、ACTIVE状態のパターンを作成する。</p>
 *
 * @param name         パターン名（早番、遅番等。2〜20文字）
 * @param startTime    勤務開始時刻
 * @param endTime      勤務終了時刻
 * @param breakMinutes 休憩時間（分。0〜120）
 * @param isOvernight  夜勤フラグ（trueなら日跨ぎパターン）
 */
public record DefinePatternCommand(
        String name,
        LocalTime startTime,
        LocalTime endTime,
        int breakMinutes,
        boolean isOvernight
) implements Command {
}
