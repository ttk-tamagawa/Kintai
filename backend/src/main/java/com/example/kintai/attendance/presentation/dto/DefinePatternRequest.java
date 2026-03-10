package com.example.kintai.attendance.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * シフトパターン作成リクエストDTO — POST /api/v1/shifts/patterns の入力値
 *
 * <p>リクエスト例:
 * <pre>
 * {
 *   "name": "早番",
 *   "startTime": "06:00",
 *   "endTime": "15:00",
 *   "breakMinutes": 60,
 *   "isOvernight": false
 * }
 * </pre>
 * </p>
 *
 * @param name         パターン名（2〜20文字、システム全体で一意）
 * @param startTime    勤務開始時刻（HH:mm形式）
 * @param endTime      勤務終了時刻（HH:mm形式）
 * @param breakMinutes 休憩時間（分。0〜120）
 * @param isOvernight  夜勤フラグ（trueなら日跨ぎパターン）
 */
public record DefinePatternRequest(
        @NotBlank(message = "パターン名は必須です")
        String name,

        @NotNull(message = "開始時刻は必須です")
        LocalTime startTime,

        @NotNull(message = "終了時刻は必須です")
        LocalTime endTime,

        @NotNull(message = "休憩時間は必須です")
        @Min(value = 0, message = "休憩時間は0分以上で指定してください")
        @Max(value = 120, message = "休憩時間は120分以下で指定してください")
        Integer breakMinutes,

        @NotNull(message = "夜勤フラグは必須です")
        Boolean isOvernight
) {}
