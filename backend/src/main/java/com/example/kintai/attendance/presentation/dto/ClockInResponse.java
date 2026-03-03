package com.example.kintai.attendance.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 出勤打刻レスポンスDTO — POST /api/v1/attendances/clock-in の返却値
 *
 * @param attendanceId 勤怠記録ID
 * @param employeeId   従業員ID
 * @param workDate     勤務日（YYYY-MM-DD）
 * @param status       勤怠ステータス（CLOCKED_IN）
 * @param clockIn      出勤時刻（UTC Instant）
 * @param source       打刻元（WEB / MOBILE）
 * @param updatedAt    更新日時（UTC Instant）
 */
public record ClockInResponse(
        UUID attendanceId,
        String employeeId,
        LocalDate workDate,
        String status,
        Instant clockIn,
        String source,
        Instant updatedAt
) {}
