package com.example.kintai.attendance.application.query;

/**
 * 月次サマリーKPI — 画面上部のKPIカード4枚分のデータ
 */
public record MonthlySummaryKpi(
        int totalWorkDays,
        double avgWorkDays,
        double totalWorkHours,
        double avgWorkHours,
        double totalOvertimeHours,
        double avgOvertimeHours,
        double totalPaidLeaveUsed
) {}
