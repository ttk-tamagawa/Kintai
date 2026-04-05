package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.attendance.domain.model.ClockType;
import com.example.kintai.shared.domain.model.ApprovalId;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * 打刻修正イベント — 承認済みの打刻修正が適用されたときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>勤務時間の再計算（修正後の打刻で再計算）</li>
 *   <li>Read Model（attendance_summaries）の打刻時刻を更新</li>
 *   <li>修正履歴の監査ログ記録</li>
 * </ul>
 * </p>
 */
public class ClockCorrectedEvent extends DomainEvent {

    /** 勤怠記録ID */
    private final AttendanceRecordId attendanceRecordId;
    /** 従業員ID */
    private final EmployeeId employeeId;
    /** 修正対象の打刻種別（CLOCK_IN/CLOCK_OUT等） */
    private final ClockType targetType;
    /** 修正前の打刻時刻 */
    private final ClockTime beforeTime;
    /** 修正後の打刻時刻 */
    private final ClockTime afterTime;
    /** 承認ID（どの承認に基づく修正か） */
    private final ApprovalId approvalId;

    /**
     * コンストラクタ — 固有フィールドのnullチェックを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public ClockCorrectedEvent(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            ClockType targetType,
            ClockTime beforeTime,
            ClockTime afterTime,
            ApprovalId approvalId
    ) {
        super();
        this.attendanceRecordId = Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        this.targetType = Objects.requireNonNull(targetType, "修正対象の打刻種別はnullにできません");
        this.beforeTime = Objects.requireNonNull(beforeTime, "修正前の打刻時刻はnullにできません");
        this.afterTime = Objects.requireNonNull(afterTime, "修正後の打刻時刻はnullにできません");
        this.approvalId = Objects.requireNonNull(approvalId, "承認IDはnullにできません");
    }

    @Override
    public String getEventType() {
        return "CLOCK_CORRECTED";
    }

    public AttendanceRecordId attendanceRecordId() { return attendanceRecordId; }
    public EmployeeId employeeId() { return employeeId; }
    public ClockType targetType() { return targetType; }
    public ClockTime beforeTime() { return beforeTime; }
    public ClockTime afterTime() { return afterTime; }
    public ApprovalId approvalId() { return approvalId; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのcorrectClock()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static ClockCorrectedEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            ClockType targetType,
            ClockTime beforeTime,
            ClockTime afterTime,
            ApprovalId approvalId
    ) {
        return new ClockCorrectedEvent(attendanceRecordId, employeeId, targetType, beforeTime, afterTime, approvalId);
    }
}
