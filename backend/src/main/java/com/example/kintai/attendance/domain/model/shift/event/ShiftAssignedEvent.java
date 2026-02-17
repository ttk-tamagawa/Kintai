package com.example.kintai.attendance.domain.model.shift.event;

import com.example.kintai.attendance.domain.model.shift.ScheduleStatus;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * シフト割当イベント — 従業員に週次シフトが割り当てられたときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（weekly_schedule_summaries）にスケジュールを反映</li>
 *   <li>PUBLISHEDの場合、対象従業員にシフト通知を送信</li>
 * </ul>
 * </p>
 *
 * @param scheduleId    スケジュールID
 * @param employeeId    対象従業員ID
 * @param weekStartDate 週の開始日（月曜日）
 * @param assignments   曜日ごとのシフトパターン割当（変更不可Map）
 * @param status        割当時のスケジュールステータス（DRAFT or PUBLISHED）
 * @param occurredAt    イベント発生日時
 */
public record ShiftAssignedEvent(
        ScheduleId scheduleId,
        EmployeeId employeeId,
        LocalDate weekStartDate,
        Map<DayOfWeek, ShiftPatternId> assignments,
        ScheduleStatus status,
        Instant occurredAt
) {

    /**
     * コンパクトコンストラクタ — nullチェックとMapの防御的コピーを行う
     */
    public ShiftAssignedEvent {
        Objects.requireNonNull(scheduleId, "スケジュールIDはnullにできません");
        Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        Objects.requireNonNull(weekStartDate, "週開始日はnullにできません");
        Objects.requireNonNull(assignments, "割当マップはnullにできません");
        Objects.requireNonNull(status, "ステータスはnullにできません");
        Objects.requireNonNull(occurredAt, "イベント発生日時はnullにできません");

        // Mapの防御的コピー — イベントは不変であるため、外部からの変更を防止する
        assignments = Collections.unmodifiableMap(new EnumMap<>(assignments));
    }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>WeeklyScheduleのassign()成功後に呼び出される。
     * イベント発生日時は自動的に現在時刻が設定される。</p>
     *
     * @param scheduleId    スケジュールID
     * @param employeeId    対象従業員ID
     * @param weekStartDate 週の開始日（月曜日）
     * @param assignments   曜日ごとのシフトパターン割当
     * @param status        スケジュールステータス
     * @return ShiftAssignedEventインスタンス
     */
    public static ShiftAssignedEvent of(
            ScheduleId scheduleId,
            EmployeeId employeeId,
            LocalDate weekStartDate,
            Map<DayOfWeek, ShiftPatternId> assignments,
            ScheduleStatus status
    ) {
        return new ShiftAssignedEvent(
                scheduleId,
                employeeId,
                weekStartDate,
                assignments,
                status,
                Instant.now()
        );
    }
}
