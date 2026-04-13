package com.example.kintai.attendance.application.query;

import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.kernel.contract.Query;

import java.time.LocalDate;

/**
 * 日次勤怠一覧取得クエリ（UC-ATT-Q02）
 *
 * <p>日次勤怠一覧画面のデータを取得するクエリへの入力パラメータをまとめる。
 * ステータスフィルタ、ソート、ページネーションに対応する。</p>
 *
 * @param employeeId    従業員ID
 * @param dateFrom      期間開始日（nullの場合は当月1日）
 * @param dateTo        期間終了日（nullの場合は当月末日）
 * @param status        ステータスフィルタ（nullの場合は全ステータス）
 * @param page          ページ番号（0始まり）
 * @param size          1ページあたりの件数
 * @param sortField     ソートフィールド（workDate, totalOvertimeMinutes, status）
 * @param sortDirection ソート方向（asc, desc）
 */
public record GetDailyAttendancesQuery(
        EmployeeId employeeId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String status,
        int page,
        int size,
        String sortField,
        String sortDirection
) implements Query {
}
