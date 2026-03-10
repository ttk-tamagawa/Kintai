package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

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
 *
 * @param attendanceRecordId 勤怠記録ID
 * @param employeeId         従業員ID
 * @param clockTime          休憩開始時刻
 * @param occurredAt         イベント発生日時
 */
public record BreakStartedEvent(
        AttendanceRecordId attendanceRecordId,
        EmployeeId employeeId,
        ClockTime clockTime,
        Instant occurredAt
) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public BreakStartedEvent {
        Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        Objects.requireNonNull(clockTime, "休憩開始時刻はnullにできません");
        Objects.requireNonNull(occurredAt, "イベント発生日時はnullにできません");
    }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのstartBreak()成功後に呼び出される。</p>
     *
     * @param attendanceRecordId 勤怠記録ID
     * @param employeeId         従業員ID
     * @param clockTime          休憩開始時刻
     * @return BreakStartedEventインスタンス
     */
    public static BreakStartedEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            ClockTime clockTime
    ) {
        return new BreakStartedEvent(
                attendanceRecordId,
                employeeId,
                clockTime,
                Instant.now()
        );
    }
}
