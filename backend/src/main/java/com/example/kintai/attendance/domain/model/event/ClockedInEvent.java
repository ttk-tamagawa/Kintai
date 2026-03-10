package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.ClockSource;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

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
 *
 * @param attendanceRecordId 勤怠記録ID
 * @param employeeId         従業員ID
 * @param workDate           勤務日
 * @param clockTime          出勤打刻時刻
 * @param source             打刻元（WEB/MOBILE）
 * @param occurredAt         イベント発生日時
 */
public record ClockedInEvent(
        AttendanceRecordId attendanceRecordId,
        EmployeeId employeeId,
        WorkDate workDate,
        ClockTime clockTime,
        ClockSource source,
        Instant occurredAt
) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public ClockedInEvent {
        Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        Objects.requireNonNull(workDate, "勤務日はnullにできません");
        Objects.requireNonNull(clockTime, "出勤打刻時刻はnullにできません");
        Objects.requireNonNull(source, "打刻元はnullにできません");
        Objects.requireNonNull(occurredAt, "イベント発生日時はnullにできません");
    }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのclockIn()成功後に呼び出される。
     * イベント発生日時は自動的に現在時刻が設定される。</p>
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
        return new ClockedInEvent(
                attendanceRecordId,
                employeeId,
                workDate,
                clockTime,
                source,
                Instant.now()
        );
    }
}
