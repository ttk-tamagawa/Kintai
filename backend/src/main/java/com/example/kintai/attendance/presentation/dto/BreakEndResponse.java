package com.example.kintai.attendance.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 休憩終了レスポンスDTO — POST /api/v1/attendances/break-end の返却値
 *
 * @param attendanceId 勤怠記録ID
 * @param employeeId   従業員ID
 * @param workDate     勤務日（YYYY-MM-DD）
 * @param status       勤怠ステータス（CLOCKED_IN）
 * @param onBreak      休憩中フラグ（false）
 * @param breakMinutes 合計休憩時間（分）
 * @param updatedAt    更新日時（UTC Instant）
 */
public record BreakEndResponse(
        UUID attendanceId,
        UUID employeeId,
        LocalDate workDate,
        String status,
        boolean onBreak,
        int breakMinutes,
        Instant updatedAt
) {}
