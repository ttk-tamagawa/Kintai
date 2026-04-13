package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository.PageResult;

/**
 * 部門ダッシュボード結果 — 当月KPI + 前月KPI + 部門別テーブル（ページネーション付き）
 */
public record DepartmentDashboardResult(
        DepartmentDashboardKpi kpi,
        DepartmentDashboardKpi previousMonth,
        PageResult<DepartmentDashboardRow> departments
) {}
