package com.example.kintai.attendance.application.query;

import com.example.kintai.shared.kernel.contract.Query;

/**
 * 部門別勤怠ダッシュボード取得クエリ（UC-ATT-Q05）
 *
 * <p>部門別勤怠ダッシュボード画面のKPI+部門別テーブルを取得するクエリへの入力パラメータをまとめる。</p>
 *
 * @param departmentId  部門IDフィルタ（nullの場合は全部門）
 * @param year          年
 * @param month         月
 * @param page          ページ番号（0始まり）
 * @param size          1ページあたりの件数
 * @param sortField     ソートフィールド
 * @param sortDirection ソート方向（asc, desc）
 */
public record GetDepartmentDashboardQuery(
        String departmentId,
        int year,
        int month,
        int page,
        int size,
        String sortField,
        String sortDirection
) implements Query {
}
