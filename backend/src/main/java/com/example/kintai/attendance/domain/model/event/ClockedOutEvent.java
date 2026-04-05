package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.ClockSource;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * 退勤打刻イベント — 従業員が退勤打刻したときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>勤務時間の自動計算（WorkDurationCalculatorの起動トリガー）</li>
 *   <li>Read Model（attendance_summaries）の退勤時刻を更新</li>
 * </ul>
 * </p>
 */
public class ClockedOutEvent extends DomainEvent {

    /** 勤怠記録ID */
    private final AttendanceRecordId attendanceRecordId;
    /** 従業員ID */
    private final EmployeeId employeeId;
    /** 退勤打刻時刻 */
    private final ClockTime clockTime;
    /** 打刻元（WEB/MOBILE） */
    private final ClockSource source;

    /**
     * コンストラクタ — 固有フィールドのnullチェックを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public ClockedOutEvent(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            ClockTime clockTime,
            ClockSource source
    ) {
        super();
        this.attendanceRecordId = Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        this.clockTime = Objects.requireNonNull(clockTime, "退勤打刻時刻はnullにできません");
        this.source = Objects.requireNonNull(source, "打刻元はnullにできません");
    }

    @Override
    public String getEventType() {
        return "CLOCKED_OUT";
    }

    public AttendanceRecordId attendanceRecordId() { return attendanceRecordId; }
    public EmployeeId employeeId() { return employeeId; }
    public ClockTime clockTime() { return clockTime; }
    public ClockSource source() { return source; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのclockOut()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static ClockedOutEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            ClockTime clockTime,
            ClockSource source
    ) {
        return new ClockedOutEvent(attendanceRecordId, employeeId, clockTime, source);
    }
}
