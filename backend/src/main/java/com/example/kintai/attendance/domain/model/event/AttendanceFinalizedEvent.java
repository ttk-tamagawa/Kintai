package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

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
 *
 * @param attendanceRecordId 勤怠記録ID
 * @param employeeId         従業員ID
 * @param workDate           勤務日
 * @param monthlyClosingId   月次締めID（どの締め処理に基づく確定か）
 * @param occurredAt         イベント発生日時
 */
public record AttendanceFinalizedEvent(
        AttendanceRecordId attendanceRecordId,
        EmployeeId employeeId,
        WorkDate workDate,
        String monthlyClosingId,
        Instant occurredAt
) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public AttendanceFinalizedEvent {
        Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        Objects.requireNonNull(workDate, "勤務日はnullにできません");
        Objects.requireNonNull(monthlyClosingId, "月次締めIDはnullにできません");
        Objects.requireNonNull(occurredAt, "イベント発生日時はnullにできません");
    }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのfinalizeRecord()成功後に呼び出される。
     * 月次締めSagaからの内部呼び出しにより発行される。</p>
     *
     * @param attendanceRecordId 勤怠記録ID
     * @param employeeId         従業員ID
     * @param workDate           勤務日
     * @param monthlyClosingId   月次締めID
     * @return AttendanceFinalizedEventインスタンス
     */
    public static AttendanceFinalizedEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            WorkDate workDate,
            String monthlyClosingId
    ) {
        return new AttendanceFinalizedEvent(
                attendanceRecordId,
                employeeId,
                workDate,
                monthlyClosingId,
                Instant.now()
        );
    }
}
