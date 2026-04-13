package com.example.kintai.attendance.application.query;

import com.example.kintai.shared.kernel.contract.Query;

/**
 * 部門別勤怠ダッシュボードCSV出力クエリ（UC-ATT-Q06）
 *
 * <p>部門別勤怠ダッシュボードをCSV形式で出力するクエリへの入力パラメータをまとめる。</p>
 *
 * @param departmentId 部門IDフィルタ（nullの場合は全部門）
 * @param year         年
 * @param month        月
 */
public record ExportDepartmentDashboardQuery(
        String departmentId,
        int year,
        int month
) implements Query {
}
