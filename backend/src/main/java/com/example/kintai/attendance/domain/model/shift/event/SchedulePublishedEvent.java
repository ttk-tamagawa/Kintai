package com.example.kintai.attendance.domain.model.shift.event;

import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * スケジュール公開イベント — 週次スケジュールがDRAFTからPUBLISHEDに遷移したときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（weekly_schedule_summaries）のステータスをPUBLISHEDに更新</li>
 *   <li>対象従業員にシフト通知を送信（将来実装予定）</li>
 * </ul>
 * </p>
 *
 * @param scheduleId    スケジュールID
 * @param employeeId    対象従業員ID
 * @param weekStartDate 週の開始日（月曜日）
 * @param occurredAt    イベント発生日時
 */
public record SchedulePublishedEvent(
        ScheduleId scheduleId,
        EmployeeId employeeId,
        LocalDate weekStartDate,
        Instant occurredAt
) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public SchedulePublishedEvent {
        Objects.requireNonNull(scheduleId, "スケジュールIDはnullにできません");
        Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        Objects.requireNonNull(weekStartDate, "週開始日はnullにできません");
        Objects.requireNonNull(occurredAt, "イベント発生日時はnullにできません");
    }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>WeeklyScheduleのpublish()成功後に呼び出される。
     * イベント発生日時は自動的に現在時刻が設定される。</p>
     *
     * @param scheduleId    スケジュールID
     * @param employeeId    対象従業員ID
     * @param weekStartDate 週の開始日（月曜日）
     * @return SchedulePublishedEventインスタンス
     */
    public static SchedulePublishedEvent of(
            ScheduleId scheduleId,
            EmployeeId employeeId,
            LocalDate weekStartDate
    ) {
        return new SchedulePublishedEvent(
                scheduleId,
                employeeId,
                weekStartDate,
                Instant.now()
        );
    }
}
