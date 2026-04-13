package com.example.kintai.attendance.application.query;

import com.example.kintai.shared.kernel.contract.Query;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 週次スケジュール一覧取得クエリ（UC-SH-Q03）
 *
 * <p>従業員と期間で週次スケジュール一覧を取得するクエリへの入力パラメータをまとめる。</p>
 *
 * @param employeeId 従業員ID（nullの場合は全従業員分を返却）
 * @param from       検索開始日（nullの場合は今週の月曜日）
 * @param to         検索終了日（nullの場合はfromから4週先の日曜日）
 */
public record GetSchedulesQuery(
        UUID employeeId,
        LocalDate from,
        LocalDate to
) implements Query {
}
