package com.example.kintai.attendance.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 退勤打刻レスポンスDTO — POST /api/v1/attendances/clock-out の返却値
 *
 * @param attendanceId    勤怠記録ID
 * @param employeeId      従業員ID
 * @param workDate        勤務日（YYYY-MM-DD）
 * @param status          勤怠ステータス（CLOCKED_OUT）
 * @param clockIn         出勤時刻（UTC Instant）
 * @param clockOut        退勤時刻（UTC Instant）
 * @param breakMinutes    休憩時間（分）
 * @param netWorkMinutes  実労働時間（分）
 * @param overtimeMinutes 残業時間（分）
 * @param source          打刻元（WEB / MOBILE）
 * @param updatedAt       更新日時（UTC Instant）
 */
public record ClockOutResponse(
        UUID attendanceId,
        String employeeId,
        LocalDate workDate,
        String status,
        Instant clockIn,
        Instant clockOut,
        int breakMinutes,
        int netWorkMinutes,
        int overtimeMinutes,
        String source,
        Instant updatedAt
) {}
