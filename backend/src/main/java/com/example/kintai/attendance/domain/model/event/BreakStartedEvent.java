package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * 休憩開始イベント — 従業員が休憩を開始したときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（attendance_summaries）のステータスを「休憩中」に更新</li>
 *   <li>リアルタイム通知（休憩開始のフィードバック）</li>
 * </ul>
 * </p>
 */
public class BreakStartedEvent extends DomainEvent {

    /** 勤怠記録ID */
    private final AttendanceRecordId attendanceRecordId;
    /** 従業員ID */
    private final EmployeeId employeeId;
    /** 休憩開始時刻 */
    private final ClockTime clockTime;

    /**
     * コンストラクタ — 固有フィールドのnullチェックを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public BreakStartedEvent(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            ClockTime clockTime
    ) {
        super();
        this.attendanceRecordId = Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        this.clockTime = Objects.requireNonNull(clockTime, "休憩開始時刻はnullにできません");
    }

    @Override
    public String getEventType() {
        return "BREAK_STARTED";
    }

    public AttendanceRecordId attendanceRecordId() { return attendanceRecordId; }
    public EmployeeId employeeId() { return employeeId; }
    public ClockTime clockTime() { return clockTime; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのstartBreak()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static BreakStartedEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            ClockTime clockTime
    ) {
        return new BreakStartedEvent(attendanceRecordId, employeeId, clockTime);
    }
}
