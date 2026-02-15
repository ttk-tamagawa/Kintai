package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.shared.domain.model.ApprovalId;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

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
 *
 * @param attendanceRecordId 勤怠記録ID
 * @param employeeId         従業員ID
 * @param workDate           勤務日
 * @param startTime          勤務開始時刻
 * @param endTime            勤務終了時刻
 * @param type               勤務種別（例: "通常勤務", "出張"）
 * @param approvalId         承認ID（どの承認に基づく登録か）
 * @param occurredAt         イベント発生日時
 */
public record ManualAttendanceRegisteredEvent(
        AttendanceRecordId attendanceRecordId,
        EmployeeId employeeId,
        WorkDate workDate,
        ClockTime startTime,
        ClockTime endTime,
        String type,
        ApprovalId approvalId,
        Instant occurredAt
) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public ManualAttendanceRegisteredEvent {
        Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        Objects.requireNonNull(workDate, "勤務日はnullにできません");
        Objects.requireNonNull(startTime, "勤務開始時刻はnullにできません");
        Objects.requireNonNull(endTime, "勤務終了時刻はnullにできません");
        Objects.requireNonNull(type, "勤務種別はnullにできません");
        Objects.requireNonNull(approvalId, "承認IDはnullにできません");
        Objects.requireNonNull(occurredAt, "イベント発生日時はnullにできません");
    }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのregisterManualAttendance()成功後に呼び出される。
     * 打刻漏れ等で出退勤記録がない場合の手動補完に使用される。</p>
     *
     * @param attendanceRecordId 勤怠記録ID
     * @param employeeId         従業員ID
     * @param workDate           勤務日
     * @param startTime          勤務開始時刻
     * @param endTime            勤務終了時刻
     * @param type               勤務種別
     * @param approvalId         承認ID
     * @return ManualAttendanceRegisteredEventインスタンス
     */
    public static ManualAttendanceRegisteredEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            WorkDate workDate,
            ClockTime startTime,
            ClockTime endTime,
            String type,
            ApprovalId approvalId
    ) {
        return new ManualAttendanceRegisteredEvent(
                attendanceRecordId,
                employeeId,
                workDate,
                startTime,
                endTime,
                type,
                approvalId,
                Instant.now()
        );
    }
}
