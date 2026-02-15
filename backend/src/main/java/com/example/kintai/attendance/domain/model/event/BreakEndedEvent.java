package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

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
 *
 * @param attendanceRecordId 勤怠記録ID
 * @param employeeId         従業員ID
 * @param clockTime          休憩終了時刻
 * @param breakMinutes       今回の休憩時間（分）— 休憩開始〜終了の差分
 * @param occurredAt         イベント発生日時
 */
public record BreakEndedEvent(
        AttendanceRecordId attendanceRecordId,
        EmployeeId employeeId,
        ClockTime clockTime,
        int breakMinutes,
        Instant occurredAt
) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public BreakEndedEvent {
        Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        Objects.requireNonNull(clockTime, "休憩終了時刻はnullにできません");
        Objects.requireNonNull(occurredAt, "イベント発生日時はnullにできません");
    }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのendBreak()成功後に呼び出される。
     * breakMinutesは休憩開始時刻と終了時刻の差分から算出される。</p>
     *
     * @param attendanceRecordId 勤怠記録ID
     * @param employeeId         従業員ID
     * @param clockTime          休憩終了時刻
     * @param breakMinutes       今回の休憩時間（分）
     * @return BreakEndedEventインスタンス
     */
    public static BreakEndedEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            ClockTime clockTime,
            int breakMinutes
    ) {
        return new BreakEndedEvent(
                attendanceRecordId,
                employeeId,
                clockTime,
                breakMinutes,
                Instant.now()
        );
    }
}
