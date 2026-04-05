package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * 休憩終了イベント — 従業員が休憩を終了したときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>休憩時間の算出（休憩開始〜終了の差分）</li>
 *   <li>Read Model（attendance_summaries）の休憩時間を更新</li>
 * </ul>
 * </p>
 */
public class BreakEndedEvent extends DomainEvent {

    /** 勤怠記録ID */
    private final AttendanceRecordId attendanceRecordId;
    /** 従業員ID */
    private final EmployeeId employeeId;
    /** 休憩終了時刻 */
    private final ClockTime clockTime;
    /** 今回の休憩時間（分）— 休憩開始〜終了の差分 */
    private final int breakMinutes;

    /**
     * コンストラクタ — 固有フィールドのnullチェックを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public BreakEndedEvent(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            ClockTime clockTime,
            int breakMinutes
    ) {
        super();
        this.attendanceRecordId = Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        this.clockTime = Objects.requireNonNull(clockTime, "休憩終了時刻はnullにできません");
        this.breakMinutes = breakMinutes;
    }

    @Override
    public String getEventType() {
        return "BREAK_ENDED";
    }

    public AttendanceRecordId attendanceRecordId() { return attendanceRecordId; }
    public EmployeeId employeeId() { return employeeId; }
    public ClockTime clockTime() { return clockTime; }
    public int breakMinutes() { return breakMinutes; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのendBreak()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static BreakEndedEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            ClockTime clockTime,
            int breakMinutes
    ) {
        return new BreakEndedEvent(attendanceRecordId, employeeId, clockTime, breakMinutes);
    }
}
