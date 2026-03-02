package com.example.kintai.attendance.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * スケジュールレスポンスDTO — スケジュール関連APIの共通返却値
 *
 * <p>以下のエンドポイントで使用する:
 * <ul>
 *   <li>POST /api/v1/shifts/schedules — スケジュール割当（201）</li>
 *   <li>PUT /api/v1/shifts/schedules/{id} — スケジュール変更（200）</li>
 *   <li>POST /api/v1/shifts/schedules/{id}/actions/publish — スケジュール公開（200）</li>
 *   <li>GET /api/v1/shifts/schedules — スケジュール一覧（200）</li>
 *   <li>GET /api/v1/shifts/schedules/{id} — スケジュール詳細（200）</li>
 * </ul>
 * </p>
 *
 * <p>assignmentsは曜日名をキーとしたMapで、各曜日のパターンID・パターン名を保持する。
 * 割当のない曜日（休み）はMapに含まれない。</p>
 *
 * @param scheduleId    スケジュールID
 * @param employeeId    従業員ID
 * @param weekStartDate 週の開始日（月曜日）
 * @param status        ステータス（DRAFT / PUBLISHED）
 * @param assignments   曜日ごとの割当（キー: MONDAY〜SUNDAY、値: DayAssignment）
 * @param assignedDays  割当日数
 * @param createdAt     作成日時（UTC Instant）
 * @param updatedAt     更新日時（UTC Instant）
 */
public record ScheduleResponse(
        UUID scheduleId,
        UUID employeeId,
        LocalDate weekStartDate,
        String status,
        Map<String, DayAssignment> assignments,
        int assignedDays,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * 曜日ごとの割当情報
     *
     * @param patternId   シフトパターンID
     * @param patternName シフトパターン名
     */
    public record DayAssignment(
            UUID patternId,
            String patternName
    ) {}
}
