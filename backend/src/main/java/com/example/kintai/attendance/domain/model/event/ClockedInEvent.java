package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.ClockSource;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * 出勤打刻イベント — 従業員が出勤打刻したときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（attendance_summaries）の出勤時刻を更新</li>
 *   <li>リアルタイム通知（打刻完了のフィードバック）</li>
 * </ul>
 * </p>
 */
public class ClockedInEvent extends DomainEvent {

    /** 勤怠記録ID */
    private final AttendanceRecordId attendanceRecordId;
    /** 従業員ID */
    private final EmployeeId employeeId;
    /** 勤務日 */
    private final WorkDate workDate;
    /** 出勤打刻時刻 */
    private final ClockTime clockTime;
    /** 打刻元（WEB/MOBILE） */
    private final ClockSource source;

    /**
     * コンストラクタ — 固有フィールドのnullチェックを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public ClockedInEvent(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            WorkDate workDate,
            ClockTime clockTime,
            ClockSource source
    ) {
        super();
        this.attendanceRecordId = Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        this.workDate = Objects.requireNonNull(workDate, "勤務日はnullにできません");
        this.clockTime = Objects.requireNonNull(clockTime, "出勤打刻時刻はnullにできません");
        this.source = Objects.requireNonNull(source, "打刻元はnullにできません");
    }

    @Override
    public String getEventType() {
        return "CLOCKED_IN";
    }

    // record スタイルの getter（参照元の変更を最小化するため get プレフィックスなし）

    public AttendanceRecordId attendanceRecordId() { return attendanceRecordId; }
    public EmployeeId employeeId() { return employeeId; }
    public WorkDate workDate() { return workDate; }
    public ClockTime clockTime() { return clockTime; }
    public ClockSource source() { return source; }

    /** 基底クラスの getOccurredAt() への互換メソッド — 参照元が event.occurredAt() を呼んでいるため */
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのclockIn()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     *
     * @param attendanceRecordId 勤怠記録ID
     * @param employeeId         従業員ID
     * @param workDate           勤務日
     * @param clockTime          出勤打刻時刻
     * @param source             打刻元
     * @return ClockedInEventインスタンス
     */
    public static ClockedInEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            WorkDate workDate,
            ClockTime clockTime,
            ClockSource source
    ) {
        return new ClockedInEvent(attendanceRecordId, employeeId, workDate, clockTime, source);
    }
}
