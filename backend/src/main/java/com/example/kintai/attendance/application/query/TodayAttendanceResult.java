package com.example.kintai.attendance.application.query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 当日勤怠ステータス — 勤怠打刻モーダルの初期表示データ
 */
public record TodayAttendanceResult(
        UUID attendanceId,
        String employeeId,
        LocalDate workDate,
        String status,
        boolean onBreak,
        Instant clockIn,
        Instant clockOut,
        int breakMinutes,
        Integer netWorkMinutes,
        Integer totalOvertimeMinutes,
        Instant updatedAt
) {}
