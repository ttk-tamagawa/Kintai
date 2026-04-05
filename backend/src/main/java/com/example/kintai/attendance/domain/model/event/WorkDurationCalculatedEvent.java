package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.OvertimeDuration;
import com.example.kintai.attendance.domain.model.WorkDuration;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * 勤務時間計算完了イベント — 勤務時間と残業時間の計算が完了したときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（attendance_summaries）の勤務時間・残業時間を更新</li>
 *   <li>月次集計（monthly_attendance_summaries）の再計算トリガー</li>
 *   <li>部門統計（department_attendance_stats）のリフレッシュトリガー</li>
 * </ul>
 * </p>
 */
public class WorkDurationCalculatedEvent extends DomainEvent {

    /** 勤怠記録ID */
    private final AttendanceRecordId attendanceRecordId;
    /** 従業員ID */
    private final EmployeeId employeeId;
    /** 計算された勤務時間（所定/実/休憩/実労働の4内訳） */
    private final WorkDuration workDuration;
    /** 計算された残業時間（通常/深夜/休日/合計の4区分） */
    private final OvertimeDuration overtimeDuration;

    /**
     * コンストラクタ — 固有フィールドのnullチェックを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public WorkDurationCalculatedEvent(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            WorkDuration workDuration,
            OvertimeDuration overtimeDuration
    ) {
        super();
        this.attendanceRecordId = Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        this.workDuration = Objects.requireNonNull(workDuration, "勤務時間はnullにできません");
        this.overtimeDuration = Objects.requireNonNull(overtimeDuration, "残業時間はnullにできません");
    }

    @Override
    public String getEventType() {
        return "WORK_DURATION_CALCULATED";
    }

    public AttendanceRecordId attendanceRecordId() { return attendanceRecordId; }
    public EmployeeId employeeId() { return employeeId; }
    public WorkDuration workDuration() { return workDuration; }
    public OvertimeDuration overtimeDuration() { return overtimeDuration; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>ドメインサービス（WorkDurationCalculator）による計算完了後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static WorkDurationCalculatedEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            WorkDuration workDuration,
            OvertimeDuration overtimeDuration
    ) {
        return new WorkDurationCalculatedEvent(attendanceRecordId, employeeId, workDuration, overtimeDuration);
    }
}
