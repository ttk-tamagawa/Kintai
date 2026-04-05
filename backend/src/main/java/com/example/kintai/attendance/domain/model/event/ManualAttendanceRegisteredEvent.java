package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.AttendanceType;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.shared.domain.model.ApprovalId;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * 手動勤務登録イベント — 承認済みの手動勤務実績が登録されたときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>勤務時間の自動計算（登録された出勤〜退勤で計算）</li>
 *   <li>Read Model（attendance_summaries）の出勤・退勤時刻を更新</li>
 *   <li>登録履歴の監査ログ記録</li>
 * </ul>
 * </p>
 */
public class ManualAttendanceRegisteredEvent extends DomainEvent {

    /** 勤怠記録ID */
    private final AttendanceRecordId attendanceRecordId;
    /** 従業員ID */
    private final EmployeeId employeeId;
    /** 勤務日 */
    private final WorkDate workDate;
    /** 勤務開始時刻 */
    private final ClockTime startTime;
    /** 勤務終了時刻 */
    private final ClockTime endTime;
    /** 勤務種別（NORMAL, BUSINESS_TRIP, REMOTE, PAID_LEAVE, ABSENCE） */
    private final AttendanceType type;
    /** 承認ID（どの承認に基づく登録か） */
    private final ApprovalId approvalId;

    /**
     * コンストラクタ — 固有フィールドのnullチェックを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public ManualAttendanceRegisteredEvent(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            WorkDate workDate,
            ClockTime startTime,
            ClockTime endTime,
            AttendanceType type,
            ApprovalId approvalId
    ) {
        super();
        this.attendanceRecordId = Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        this.workDate = Objects.requireNonNull(workDate, "勤務日はnullにできません");
        this.startTime = Objects.requireNonNull(startTime, "勤務開始時刻はnullにできません");
        this.endTime = Objects.requireNonNull(endTime, "勤務終了時刻はnullにできません");
        this.type = Objects.requireNonNull(type, "勤務種別はnullにできません");
        this.approvalId = Objects.requireNonNull(approvalId, "承認IDはnullにできません");
    }

    @Override
    public String getEventType() {
        return "MANUAL_ATTENDANCE_REGISTERED";
    }

    public AttendanceRecordId attendanceRecordId() { return attendanceRecordId; }
    public EmployeeId employeeId() { return employeeId; }
    public WorkDate workDate() { return workDate; }
    public ClockTime startTime() { return startTime; }
    public ClockTime endTime() { return endTime; }
    public AttendanceType type() { return type; }
    public ApprovalId approvalId() { return approvalId; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのregisterManualAttendance()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static ManualAttendanceRegisteredEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            WorkDate workDate,
            ClockTime startTime,
            ClockTime endTime,
            AttendanceType type,
            ApprovalId approvalId
    ) {
        return new ManualAttendanceRegisteredEvent(
                attendanceRecordId, employeeId, workDate, startTime, endTime, type, approvalId);
    }
}
