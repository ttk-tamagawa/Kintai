package com.example.kintai.attendance.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 打刻修正リクエストDTO — POST /api/v1/internal/attendances/correct のリクエストボディ
 *
 * <p>承認済みの打刻修正申請に基づき、打刻時刻を修正する。
 * 修正対象の勤怠記録ID、修正対象の打刻種別、修正後の時刻、修正理由、承認IDを受け取る。</p>
 *
 * @param attendanceId  修正対象の勤怠記録ID（必須）
 * @param targetType    修正対象の打刻種別（必須）— "CLOCK_IN" / "CLOCK_OUT" / "BREAK_START" / "BREAK_END"
 * @param correctedTime 修正後の打刻時刻（必須、ISO 8601形式）
 * @param reason        修正理由（必須）
 * @param approvalId    承認ID（必須）— 承認済みの申請を紐づける
 */
public record CorrectClockRequest(
        @NotNull UUID attendanceId,
        @NotNull String targetType,
        @NotNull OffsetDateTime correctedTime,
        @NotNull String reason,
        @NotNull UUID approvalId
) {}
