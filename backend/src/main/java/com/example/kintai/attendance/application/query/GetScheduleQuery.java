package com.example.kintai.attendance.application.query;

import com.example.kintai.shared.kernel.contract.Query;

import java.util.UUID;

/**
 * 週次スケジュール詳細取得クエリ（UC-SH-Q04）
 *
 * <p>スケジュールの詳細を取得するクエリへの入力パラメータをまとめる。</p>
 *
 * @param scheduleId スケジュールID
 */
public record GetScheduleQuery(
        UUID scheduleId
) implements Query {
}
