package com.example.kintai.attendance.application.query;

/**
 * 月次サマリー行 — 従業員別テーブルの1行（分→時間変換済み）
 */
public record MonthlySummaryRow(
        String employeeId,
        String employeeName,
        int workDays,
        double totalWorkHours,
        double totalOvertimeHours,
        double lateNightHours,
        double paidLeaveUsed
) {}
