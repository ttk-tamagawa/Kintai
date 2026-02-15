package com.example.kintai.attendance.domain.model.event;

import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.attendance.domain.model.ClockType;
import com.example.kintai.shared.domain.model.ApprovalId;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

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
 *
 * @param attendanceRecordId 勤怠記録ID
 * @param employeeId         従業員ID
 * @param targetType         修正対象の打刻種別（CLOCK_IN/CLOCK_OUT等）
 * @param beforeTime         修正前の打刻時刻
 * @param afterTime          修正後の打刻時刻
 * @param approvalId         承認ID（どの承認に基づく修正か）
 * @param occurredAt         イベント発生日時
 */
public record ClockCorrectedEvent(
        AttendanceRecordId attendanceRecordId,
        EmployeeId employeeId,
        ClockType targetType,
        ClockTime beforeTime,
        ClockTime afterTime,
        ApprovalId approvalId,
        Instant occurredAt
) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public ClockCorrectedEvent {
        Objects.requireNonNull(attendanceRecordId, "勤怠記録IDはnullにできません");
        Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        Objects.requireNonNull(targetType, "修正対象の打刻種別はnullにできません");
        Objects.requireNonNull(beforeTime, "修正前の打刻時刻はnullにできません");
        Objects.requireNonNull(afterTime, "修正後の打刻時刻はnullにできません");
        Objects.requireNonNull(approvalId, "承認IDはnullにできません");
        Objects.requireNonNull(occurredAt, "イベント発生日時はnullにできません");
    }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>AttendanceRecordのcorrectClock()成功後に呼び出される。
     * 修正前の時刻はアプリケーション層で元の打刻から取得する。</p>
     *
     * @param attendanceRecordId 勤怠記録ID
     * @param employeeId         従業員ID
     * @param targetType         修正対象の打刻種別
     * @param beforeTime         修正前の打刻時刻
     * @param afterTime          修正後の打刻時刻
     * @param approvalId         承認ID
     * @return ClockCorrectedEventインスタンス
     */
    public static ClockCorrectedEvent of(
            AttendanceRecordId attendanceRecordId,
            EmployeeId employeeId,
            ClockType targetType,
            ClockTime beforeTime,
            ClockTime afterTime,
            ApprovalId approvalId
    ) {
        return new ClockCorrectedEvent(
                attendanceRecordId,
                employeeId,
                targetType,
                beforeTime,
                afterTime,
                approvalId,
                Instant.now()
        );
    }
}
