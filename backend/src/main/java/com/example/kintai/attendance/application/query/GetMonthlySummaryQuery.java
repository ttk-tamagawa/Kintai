package com.example.kintai.attendance.application.query;

import com.example.kintai.shared.kernel.contract.Query;

/**
 * 月次勤怠サマリー取得クエリ（UC-ATT-Q03）
 *
 * <p>月次勤怠サマリー画面のKPI+従業員別テーブルを取得するクエリへの入力パラメータをまとめる。</p>
 *
 * @param departmentId  部門ID
 * @param year          年
 * @param month         月
 * @param page          ページ番号（0始まり）
 * @param size          1ページあたりの件数
 * @param sortField     ソートフィールド（employeeName, totalWorkHours, totalOvertimeHours）
 * @param sortDirection ソート方向（asc, desc）
 */
public record GetMonthlySummaryQuery(
        String departmentId,
        int year,
        int month,
        int page,
        int size,
        String sortField,
        String sortDirection
) implements Query {
}
