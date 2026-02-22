package com.example.kintai.attendance.presentation.dto;

import java.util.List;

/**
 * 部門別勤怠ダッシュボードレスポンスDTO — GET /api/v1/attendances/department-dashboard の返却値
 *
 * <p>部門別勤怠ダッシュボード画面（SCR-ATT-004）で使用する。
 * 当月KPI、前月KPI（トレンド比較用）、部門別テーブル（ページネーション付き）を返却する。</p>
 *
 * @param kpi           当月のKPIカードデータ
 * @param previousMonth 前月のKPIカードデータ（トレンド比較用）
 * @param departments   部門別テーブル（ページネーション付き）
 */
public record DepartmentDashboardResponse(
        DepartmentKpiDto kpi,
        DepartmentKpiDto previousMonth,
        PagedDepartments departments
) {
    /**
     * 部門ダッシュボードKPI — 画面上部の4枚のKPIカード用データ
     *
     * @param avgOvertimeHours      平均残業時間（時間）
     * @param totalOvertimeAlertCount 36協定違反件数
     * @param totalMissingClockCount  未打刻件数
     * @param avgAttendanceRate     平均出勤率（%）
     */
    public record DepartmentKpiDto(
            double avgOvertimeHours,
            int totalOvertimeAlertCount,
            int totalMissingClockCount,
            double avgAttendanceRate
    ) {}

    /**
     * 部門別ページ — ページネーション付き部門テーブル
     *
     * @param content       現在ページの部門行
     * @param page          ページ番号（0始まり）
     * @param size          1ページあたりの件数
     * @param totalElements 全件数
     * @param totalPages    総ページ数
     */
    public record PagedDepartments(
            List<DepartmentRow> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    /**
     * 部門別ダッシュボード行 — テーブルの1行（分→時間変換済み）
     *
     * @param departmentId       部門ID
     * @param departmentName     部門名
     * @param headCount          人数
     * @param avgOvertimeHours   平均残業時間（時間）
     * @param maxOvertimeHours   最大残業時間（時間）
     * @param overtimeAlertCount 残業アラート件数
     * @param missingClockCount  打刻漏れ件数
     * @param attendanceRate     出勤率（%）
     */
    public record DepartmentRow(
            String departmentId,
            String departmentName,
            int headCount,
            double avgOvertimeHours,
            double maxOvertimeHours,
            int overtimeAlertCount,
            int missingClockCount,
            double attendanceRate
    ) {}
}
