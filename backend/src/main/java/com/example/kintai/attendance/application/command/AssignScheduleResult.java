package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.WeeklySchedule;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import java.util.Map;

/**
 * スケジュール割当/変更の実行結果 — Write Modelから直接レスポンスに必要な情報を保持する
 *
 * <p>AFTER_COMMITプロジェクターのタイミング問題を回避するため、
 * Read Modelに依存せずWrite Modelからレスポンスを構築する。
 * AssignScheduleUseCase と ChangeScheduleUseCase で共有する。</p>
 *
 * @param schedule     保存済みの週次スケジュール（Write Model）
 * @param patternNames パターンID→パターン名のマップ（レスポンス用）
 */
public record AssignScheduleResult(
        WeeklySchedule schedule,
        Map<ShiftPatternId, String> patternNames
) {
}
