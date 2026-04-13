package com.example.kintai.attendance.application.query;

/**
 * 部門ダッシュボードKPI — 画面上部のKPIカード4枚分のデータ
 */
public record DepartmentDashboardKpi(
        double avgOvertimeHours,
        int totalOvertimeAlertCount,
        int totalMissingClockCount,
        double avgAttendanceRate
) {}
