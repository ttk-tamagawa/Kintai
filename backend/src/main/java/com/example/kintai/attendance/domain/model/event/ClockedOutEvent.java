package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.ClockSource;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

import java.time.Instant;
import java.util.Objects;

/**
 * 退勤打刻イベント — 従業員が退勤打刻したときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>勤務時間の自動計算（WorkDurationCalculatorの起動トリガー）</li>
 *   <li>Read Model（attendance_summaries）の退勤時刻を更新</li>
 * </ul>
 * </p>
 *
 * @param attendanceRecordId 勤怠記録ID
 * @param employeeId         従業員ID
 * @param clockTime          退勤打刻時刻
 * @param source             打刻元（WEB/MOBILE）
 * @param occurredAt         イベント発生日時
 */
public record ClockedOutEvent(
        AttendanceRecordId attendanceRecordId,
        EmployeeId employeeId,
        ClockTime clockTime,
        ClockSource source,
        Instant occurredAt
) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public ClockedOutEvent {
        Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        Objects.requireNonNull(clockTime, "退勤打刻時刻はnullにできません");
        Objects.requireNonNull(source, "打刻元はnullにできません");
        Objects.requireNonNull(occurredAt, "イベント発生日時はnullにできません");
    }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのclockOut()成功後に呼び出される。
     * このイベントをトリガーに勤務時間の自動計算が実行される。</p>
     *
     * @param attendanceRecordId 勤怠記録ID
     * @param employeeId         従業員ID
     * @param clockTime          退勤打刻時刻
     * @param source             打刻元
     * @return ClockedOutEventインスタンス
     */
    public static ClockedOutEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            ClockTime clockTime,
            ClockSource source
    ) {
        return new ClockedOutEvent(
                attendanceRecordId,
                employeeId,
                clockTime,
                source,
                Instant.now()
        );
    }
}
