package com.example.kintai.attendance.domain.model.shift.event;

import com.example.kintai.attendance.domain.model.shift.ScheduleStatus;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * シフト変更イベント — 既存の週次シフトが変更されたときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（weekly_schedule_summaries）のスケジュールを更新</li>
 *   <li>PUBLISHEDからDRAFTに戻った場合、管理者に再公開が必要な旨を通知</li>
 * </ul>
 * </p>
 *
 * <p>previousStatusがPUBLISHEDの場合、変更後はDRAFTに戻る。
 * 再公開するまで従業員には変更が反映されない。</p>
 */
public class ShiftChangedEvent extends DomainEvent {

    /** スケジュールID */
    private final ScheduleId scheduleId;
    /** 対象従業員ID */
    private final EmployeeId employeeId;
    /** 変更後の曜日ごと割当（変更不可Map） */
    private final Map<DayOfWeek, ShiftPatternId> changedDays;
    /** 変更前のステータス（DRAFTまたはPUBLISHED） */
    private final ScheduleStatus previousStatus;

    /**
     * コンストラクタ — nullチェックとMapの防御的コピーを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public ShiftChangedEvent(
            ScheduleId scheduleId,
            EmployeeId employeeId,
            Map<DayOfWeek, ShiftPatternId> changedDays,
            ScheduleStatus previousStatus
    ) {
        super();
        this.scheduleId = Objects.requireNonNull(scheduleId, "スケジュールIDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        Objects.requireNonNull(changedDays, "変更日マップはnullにできません");
        // Mapの防御的コピー — イベントは不変であるため、外部からの変更を防止する
        this.changedDays = Collections.unmodifiableMap(new EnumMap<>(changedDays));
        this.previousStatus = Objects.requireNonNull(previousStatus, "変更前ステータスはnullにできません");
    }

    @Override
    public String getEventType() {
        return "CHANGED";
    }

    public ScheduleId scheduleId() { return scheduleId; }
    public EmployeeId employeeId() { return employeeId; }
    public Map<DayOfWeek, ShiftPatternId> changedDays() { return changedDays; }
    public ScheduleStatus previousStatus() { return previousStatus; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>WeeklyScheduleのchangeAssignments()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static ShiftChangedEvent of(
            ScheduleId scheduleId,
            EmployeeId employeeId,
            Map<DayOfWeek, ShiftPatternId> changedDays,
            ScheduleStatus previousStatus
    ) {
        return new ShiftChangedEvent(scheduleId, employeeId, changedDays, previousStatus);
    }
}
