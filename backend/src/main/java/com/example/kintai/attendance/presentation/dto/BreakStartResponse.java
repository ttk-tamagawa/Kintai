package com.example.kintai.attendance.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 休憩開始レスポンスDTO — POST /api/v1/attendances/break-start の返却値
 *
 * @param attendanceId     勤怠記録ID
 * @param employeeId       従業員ID
 * @param workDate         勤務日（YYYY-MM-DD）
 * @param status           勤怠ステータス（CLOCKED_IN）
 * @param onBreak          休憩中フラグ（true）
 * @param currentBreakStart 今回の休憩開始時刻（UTC Instant）
 * @param updatedAt        更新日時（UTC Instant）
 */
public record BreakStartResponse(
        UUID attendanceId,
        String employeeId,
        LocalDate workDate,
        String status,
        boolean onBreak,
        Instant currentBreakStart,
        Instant updatedAt
) {}
