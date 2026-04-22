package com.example.kintai.attendance.application.query;

import com.example.kintai.attendance.application.query.AttendanceFinder.PageResult;

/**
 * 月次サマリー結果 — KPI + 従業員別テーブル（ページネーション付き）
 */
public record MonthlySummaryResult(
        MonthlySummaryKpi kpi,
        PageResult<MonthlySummaryRow> employees
) {}
