package com.example.kintai.attendance.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 手動勤務登録レスポンスDTO — POST /api/v1/internal/attendances/register の返却値
 *
 * <p>手動登録後の勤怠記録の状態を返却する。
 * 出勤+退勤が一括登録されるため、勤務時間が計算済みで返却される。</p>
 *
 * @param attendanceId       勤怠記録ID
 * @param employeeId         従業員ID
 * @param workDate           勤務日（YYYY-MM-DD）
 * @param status             勤怠ステータス（CLOCKED_OUT）
 * @param startTime          勤務開始時刻（UTC Instant）
 * @param endTime            勤務終了時刻（UTC Instant）
 * @param breakMinutes       休憩時間（分）
 * @param netWorkMinutes     正味勤務時間（分）
 * @param totalOvertimeMinutes 合計残業時間（分）
 * @param updatedAt          更新日時（UTC Instant）
 */
public record RegisterManualAttendanceResponse(
        UUID attendanceId,
        UUID employeeId,
        LocalDate workDate,
        String status,
        Instant startTime,
        Instant endTime,
        int breakMinutes,
        int netWorkMinutes,
        int totalOvertimeMinutes,
        Instant updatedAt
) {}
