package com.example.kintai.attendance.application.query;

/**
 * 部門ダッシュボード行 — 部門別テーブルの1行（分→時間変換済み）
 */
public record DepartmentDashboardRow(
        String departmentId,
        String departmentName,
        int headCount,
        double avgOvertimeHours,
        double maxOvertimeHours,
        int overtimeAlertCount,
        int missingClockCount,
        double attendanceRate
) {}
