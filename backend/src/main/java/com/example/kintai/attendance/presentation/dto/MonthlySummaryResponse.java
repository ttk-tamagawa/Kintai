package com.example.kintai.attendance.presentation.dto;

import java.util.List;
import java.util.UUID;

/**
 * 月次勤怠サマリーレスポンスDTO — GET /api/v1/attendances/monthly-summary の返却値
 *
 * <p>月次勤怠サマリー画面（SCR-ATT-003）で使用する。
 * KPIカード4枚分のデータと、従業員別サマリーテーブル（ページネーション付き）を返却する。</p>
 *
 * @param kpi       KPIカードデータ（全従業員から集計）
 * @param employees 従業員別サマリーテーブル（ページネーション付き）
 */
public record MonthlySummaryResponse(
        MonthlySummaryKpiDto kpi,
        PagedEmployees employees
) {
    /**
     * 月次サマリーKPI — 画面上部の4枚のKPIカード用データ
     *
     * @param totalWorkDays      総出勤日数
     * @param avgWorkDays        平均出勤日数
     * @param totalWorkHours     総労働時間（時間）
     * @param avgWorkHours       平均労働時間（時間）
     * @param totalOvertimeHours 総残業時間（時間）
     * @param avgOvertimeHours   平均残業時間（時間）
     * @param totalPaidLeaveUsed 総有給消化日数
     */
    public record MonthlySummaryKpiDto(
            int totalWorkDays,
            double avgWorkDays,
            double totalWorkHours,
            double avgWorkHours,
            double totalOvertimeHours,
            double avgOvertimeHours,
            double totalPaidLeaveUsed
    ) {}

    /**
     * 従業員別サマリーページ — ページネーション付き従業員テーブル
     *
     * @param content       現在ページの従業員行
     * @param page          ページ番号（0始まり）
     * @param size          1ページあたりの件数
     * @param totalElements 全件数
     * @param totalPages    総ページ数
     */
    public record PagedEmployees(
            List<EmployeeRow> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    /**
     * 従業員別サマリー行 — テーブルの1行（分→時間変換済み）
     *
     * @param employeeId       従業員ID
     * @param employeeName     従業員名
     * @param workDays         出勤日数
     * @param totalWorkHours   総労働時間（時間）
     * @param totalOvertimeHours 総残業時間（時間）
     * @param lateNightHours   深夜時間（時間）
     * @param paidLeaveUsed    有給消化日数
     */
    public record EmployeeRow(
            UUID employeeId,
            String employeeName,
            int workDays,
            double totalWorkHours,
            double totalOvertimeHours,
            double lateNightHours,
            double paidLeaveUsed
    ) {}
}
