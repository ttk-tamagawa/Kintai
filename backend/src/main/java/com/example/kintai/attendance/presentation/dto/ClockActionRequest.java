package com.example.kintai.attendance.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 打刻リクエストDTO — 4つの打刻APIで共通に使用する
 *
 * <p>出勤・退勤・休憩開始・休憩終了の全エンドポイントで同一のリクエスト形式を使用する。
 * shiftPatternIdは出勤打刻時のみ任意で指定する（他のエンドポイントでは無視される）。</p>
 *
 * <p>リクエスト例:
 * <pre>
 * {
 *   "employeeId": "550e8400-e29b-41d4-a716-446655440000",
 *   "clockTime": "2024-04-01T09:00:00+09:00",
 *   "source": "WEB",
 *   "shiftPatternId": null
 * }
 * </pre>
 * </p>
 *
 * @param employeeId     従業員ID（必須）
 * @param clockTime      打刻時刻（ISO 8601形式、必須）
 * @param source         打刻元（"WEB" または "MOBILE"、必須）
 * @param shiftPatternId シフトパターンID（出勤打刻時のみ使用、任意）
 */
public record ClockActionRequest(
        @NotNull(message = "従業員IDは必須です")
        UUID employeeId,

        @NotNull(message = "打刻時刻は必須です")
        OffsetDateTime clockTime,

        @NotNull(message = "打刻元は必須です")
        String source,

        UUID shiftPatternId  // nullable: 出勤打刻時のみ使用
) {}
