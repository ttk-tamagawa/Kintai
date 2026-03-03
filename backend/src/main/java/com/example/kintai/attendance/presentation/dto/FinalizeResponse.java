package com.example.kintai.attendance.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 本締め確定レスポンスDTO — POST /api/v1/internal/attendances/finalize の返却値
 *
 * <p>確定後の勤怠記録の状態を返却する。
 * ステータスはFINALIZEDとなり、以降の変更は不可となる。</p>
 *
 * @param attendanceId    勤怠記録ID
 * @param employeeId      従業員ID
 * @param workDate        勤務日（YYYY-MM-DD）
 * @param status          勤怠ステータス（FINALIZED）
 * @param monthlyClosingId 月次締めID（UUID）
 * @param updatedAt       更新日時（UTC Instant）
 */
public record FinalizeResponse(
        UUID attendanceId,
        String employeeId,
        LocalDate workDate,
        String status,
        UUID monthlyClosingId,
        Instant updatedAt
) {}
