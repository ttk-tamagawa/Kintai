package com.example.kintai.attendance.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * スケジュール割当リクエストDTO — POST /api/v1/shifts/schedules の入力値
 *
 * <p>リクエスト例:
 * <pre>
 * {
 *   "employeeId": "550e8400-e29b-41d4-a716-446655440000",
 *   "weekStartDate": "2026-02-23",
 *   "assignments": {
 *     "MONDAY": "a1b2c3d4-e5f6-...",
 *     "TUESDAY": "a1b2c3d4-e5f6-...",
 *     "WEDNESDAY": "b2c3d4e5-f6a7-..."
 *   }
 * }
 * </pre>
 * </p>
 *
 * <p>assignmentsのキーは曜日名（MONDAY〜SUNDAY）、値はシフトパターンIDを指定する。
 * 割当がない曜日は省略する（休みとして扱われる）。</p>
 *
 * @param employeeId    従業員ID（必須）
 * @param weekStartDate 週の開始日（月曜日、必須）
 * @param assignments   曜日ごとのシフトパターンID割当（最低1日必須）
 */
public record AssignScheduleRequest(
        @NotNull(message = "従業員IDは必須です")
        String employeeId,

        @NotNull(message = "週開始日は必須です")
        LocalDate weekStartDate,

        @NotNull(message = "割当は必須です")
        @Size(min = 1, message = "最低1日はシフトパターンを割り当ててください")
        Map<String, UUID> assignments
) {}
