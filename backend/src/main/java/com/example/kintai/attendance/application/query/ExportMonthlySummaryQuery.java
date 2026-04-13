package com.example.kintai.attendance.application.query;

import com.example.kintai.shared.kernel.contract.Query;

/**
 * 月次勤怠サマリーCSV出力クエリ（UC-ATT-Q04）
 *
 * <p>月次勤怠サマリーをCSV形式で出力するクエリへの入力パラメータをまとめる。</p>
 *
 * @param departmentId 部門ID
 * @param year         年
 * @param month        月
 */
public record ExportMonthlySummaryQuery(
        String departmentId,
        int year,
        int month
) implements Query {
}
