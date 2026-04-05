package com.example.kintai.attendance.domain.model.shift.event;

import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * スケジュール非公開イベント — 週次スケジュールがPUBLISHEDからDRAFTに戻されたときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（weekly_schedule_summaries）のステータスをDRAFTに更新</li>
 * </ul>
 * </p>
 */
public class ScheduleUnpublishedEvent extends DomainEvent {

    /** スケジュールID */
    private final ScheduleId scheduleId;
    /** 対象従業員ID */
    private final EmployeeId employeeId;
    /** 週の開始日（月曜日） */
    private final LocalDate weekStartDate;

    /**
     * コンストラクタ — 固有フィールドのnullチェックを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public ScheduleUnpublishedEvent(
            ScheduleId scheduleId,
            EmployeeId employeeId,
            LocalDate weekStartDate
    ) {
        super();
        this.scheduleId = Objects.requireNonNull(scheduleId, "スケジュールIDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        this.weekStartDate = Objects.requireNonNull(weekStartDate, "週開始日はnullにできません");
    }

    @Override
    public String getEventType() {
        return "UNPUBLISHED";
    }

    public ScheduleId scheduleId() { return scheduleId; }
    public EmployeeId employeeId() { return employeeId; }
    public LocalDate weekStartDate() { return weekStartDate; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>WeeklyScheduleのunpublish()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static ScheduleUnpublishedEvent of(
            ScheduleId scheduleId,
            EmployeeId employeeId,
            LocalDate weekStartDate
    ) {
        return new ScheduleUnpublishedEvent(scheduleId, employeeId, weekStartDate);
    }
}
