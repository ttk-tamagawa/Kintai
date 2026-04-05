package com.example.kintai.attendance.domain.model.shift.event;

import com.example.kintai.attendance.domain.model.shift.ScheduleStatus;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

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
 */
public class ShiftAssignedEvent extends DomainEvent {

    /** スケジュールID */
    private final ScheduleId scheduleId;
    /** 対象従業員ID */
    private final EmployeeId employeeId;
    /** 週の開始日（月曜日） */
    private final LocalDate weekStartDate;
    /** 曜日ごとのシフトパターン割当（変更不可Map） */
    private final Map<DayOfWeek, ShiftPatternId> assignments;
    /** 割当時のスケジュールステータス（DRAFT or PUBLISHED） */
    private final ScheduleStatus status;

    /**
     * コンストラクタ — nullチェックとMapの防御的コピーを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public ShiftAssignedEvent(
            ScheduleId scheduleId,
            EmployeeId employeeId,
            LocalDate weekStartDate,
            Map<DayOfWeek, ShiftPatternId> assignments,
            ScheduleStatus status
    ) {
        super();
        this.scheduleId = Objects.requireNonNull(scheduleId, "スケジュールIDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        this.weekStartDate = Objects.requireNonNull(weekStartDate, "週開始日はnullにできません");
        Objects.requireNonNull(assignments, "割当マップはnullにできません");
        // Mapの防御的コピー — イベントは不変であるため、外部からの変更を防止する
        this.assignments = Collections.unmodifiableMap(new EnumMap<>(assignments));
        this.status = Objects.requireNonNull(status, "ステータスはnullにできません");
    }

    @Override
    public String getEventType() {
        return "ASSIGNED";
    }

    public ScheduleId scheduleId() { return scheduleId; }
    public EmployeeId employeeId() { return employeeId; }
    public LocalDate weekStartDate() { return weekStartDate; }
    public Map<DayOfWeek, ShiftPatternId> assignments() { return assignments; }
    public ScheduleStatus status() { return status; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>WeeklyScheduleのassign()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static ShiftAssignedEvent of(
            ScheduleId scheduleId,
            EmployeeId employeeId,
            LocalDate weekStartDate,
            Map<DayOfWeek, ShiftPatternId> assignments,
            ScheduleStatus status
    ) {
        return new ShiftAssignedEvent(scheduleId, employeeId, weekStartDate, assignments, status);
    }
}
