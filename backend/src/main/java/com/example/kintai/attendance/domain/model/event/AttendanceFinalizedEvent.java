package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.MonthlyClosingId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * 勤怠確定イベント — 月次本締めにより勤怠記録が確定されたときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（attendance_summaries）のステータスをFINALIZEDに更新</li>
 *   <li>月次集計の確定処理</li>
 *   <li>給与計算システムへの連携トリガー（将来実装）</li>
 * </ul>
 * </p>
 */
public class AttendanceFinalizedEvent extends DomainEvent {

    /** 勤怠記録ID */
    private final AttendanceRecordId attendanceRecordId;
    /** 従業員ID */
    private final EmployeeId employeeId;
    /** 勤務日 */
    private final WorkDate workDate;
    /** 月次締めID（どの締め処理に基づく確定か） */
    private final MonthlyClosingId monthlyClosingId;

    /**
     * コンストラクタ — 固有フィールドのnullチェックを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public AttendanceFinalizedEvent(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            WorkDate workDate,
            MonthlyClosingId monthlyClosingId
    ) {
        super();
        this.attendanceRecordId = Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        this.workDate = Objects.requireNonNull(workDate, "勤務日はnullにできません");
        this.monthlyClosingId = Objects.requireNonNull(monthlyClosingId, "月次締めIDはnullにできません");
    }

    @Override
    public String getEventType() {
        return "ATTENDANCE_FINALIZED";
    }

    public AttendanceRecordId attendanceRecordId() { return attendanceRecordId; }
    public EmployeeId employeeId() { return employeeId; }
    public WorkDate workDate() { return workDate; }
    public MonthlyClosingId monthlyClosingId() { return monthlyClosingId; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのfinalizeRecord()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static AttendanceFinalizedEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            WorkDate workDate,
            MonthlyClosingId monthlyClosingId
    ) {
        return new AttendanceFinalizedEvent(attendanceRecordId, employeeId, workDate, monthlyClosingId);
    }
}
