package com.example.kintai.attendance.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 本締め確定リクエストDTO — POST /api/v1/internal/attendances/finalize のリクエストボディ
 *
 * <p>月次本締め処理により、勤怠記録のステータスをCLOCKED_OUT→FINALIZEDに遷移させる。
 * 確定後は一切の変更が不可となる。</p>
 *
 * @param attendanceId   確定対象の勤怠記録ID（必須）
 * @param monthlyClosingId 月次締めID（必須、UUID）— どの締め処理に基づく確定かを紐づける
 */
public record FinalizeRequest(
        @NotNull UUID attendanceId,
        @NotNull UUID monthlyClosingId
) {}
