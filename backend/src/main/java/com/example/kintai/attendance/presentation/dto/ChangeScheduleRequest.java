package com.example.kintai.attendance.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

/**
 * スケジュール変更リクエストDTO — PUT /api/v1/shifts/schedules/{scheduleId} の入力値
 *
 * <p>リクエスト例:
 * <pre>
 * {
 *   "assignments": {
 *     "MONDAY": "a1b2c3d4-e5f6-...",
 *     "TUESDAY": "b2c3d4e5-f6a7-...",
 *     "FRIDAY": "c3d4e5f6-a7b8-..."
 *   }
 * }
 * </pre>
 * </p>
 *
 * <p>assignmentsのキーは曜日名（MONDAY〜SUNDAY）、値はシフトパターンIDを指定する。
 * 変更後の全割当を送信する（差分ではなく全量置換）。</p>
 *
 * @param assignments 曜日ごとのシフトパターンID割当（最低1日必須）
 */
public record ChangeScheduleRequest(
        @NotNull(message = "割当は必須です")
        @Size(min = 1, message = "最低1日はシフトパターンを割り当ててください")
        Map<String, UUID> assignments
) {}
