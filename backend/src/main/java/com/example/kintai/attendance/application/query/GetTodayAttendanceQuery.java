package com.example.kintai.attendance.application.query;

import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.kernel.contract.Query;

/**
 * 当日勤怠ステータス取得クエリ（UC-ATT-Q01）
 *
 * <p>勤怠打刻モーダルの初期表示データを取得するクエリへの入力パラメータをまとめる。</p>
 *
 * @param employeeId 従業員ID
 */
public record GetTodayAttendanceQuery(
        EmployeeId employeeId
) implements Query {
}
