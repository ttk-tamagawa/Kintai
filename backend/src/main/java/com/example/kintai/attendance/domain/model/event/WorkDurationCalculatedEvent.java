package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.OvertimeDuration;
import com.example.kintai.attendance.domain.model.WorkDuration;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

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
 *
 * @param attendanceRecordId 勤怠記録ID
 * @param employeeId         従業員ID
 * @param workDuration       計算された勤務時間（所定/実/休憩/実労働の4内訳）
 * @param overtimeDuration   計算された残業時間（通常/深夜/休日/合計の4区分）
 * @param occurredAt         イベント発生日時
 */
public record WorkDurationCalculatedEvent(
        AttendanceRecordId attendanceRecordId,
        EmployeeId employeeId,
        WorkDuration workDuration,
        OvertimeDuration overtimeDuration,
        Instant occurredAt
) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public WorkDurationCalculatedEvent {
        Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        Objects.requireNonNull(workDuration, "勤務時間はnullにできません");
        Objects.requireNonNull(overtimeDuration, "残業時間はnullにできません");
        Objects.requireNonNull(occurredAt, "イベント発生日時はnullにできません");
    }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>ドメインサービス（WorkDurationCalculator）による計算完了後に呼び出される。
     * 退勤打刻時および打刻修正時にトリガーされる。</p>
     *
     * @param attendanceRecordId 勤怠記録ID
     * @param employeeId         従業員ID
     * @param workDuration       計算された勤務時間
     * @param overtimeDuration   計算された残業時間
     * @return WorkDurationCalculatedEventインスタンス
     */
    public static WorkDurationCalculatedEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            WorkDuration workDuration,
            OvertimeDuration overtimeDuration
    ) {
        return new WorkDurationCalculatedEvent(
                attendanceRecordId,
                employeeId,
                workDuration,
                overtimeDuration,
                Instant.now()
        );
    }
}
