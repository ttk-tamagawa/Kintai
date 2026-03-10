package com.example.kintai.attendance.domain.model.shift.event;

import com.example.kintai.attendance.domain.model.shift.ScheduleStatus;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * シフト変更イベント — 既存の週次シフトが変更されたときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（weekly_schedule_summaries）のスケジュールを更新</li>
 *   <li>PUBLISHEDからDRAFTに戻った場合、管理者に再公開が必要な旨を通知</li>
 * </ul>
 * </p>
 *
 * <p>previousStatusがPUBLISHEDの場合、変更後はDRAFTに戻る。
 * 再公開するまで従業員には変更が反映されない。</p>
 *
 * @param scheduleId     スケジュールID
 * @param employeeId     対象従業員ID
 * @param changedDays    変更後の曜日ごと割当（変更不可Map）
 * @param previousStatus 変更前のステータス（DRAFTまたはPUBLISHED）
 * @param occurredAt     イベント発生日時
 */
public record ShiftChangedEvent(
        ScheduleId scheduleId,
        EmployeeId employeeId,
        Map<DayOfWeek, ShiftPatternId> changedDays,
        ScheduleStatus previousStatus,
        Instant occurredAt
) {

    /**
     * コンパクトコンストラクタ — nullチェックとMapの防御的コピーを行う
     */
    public ShiftChangedEvent {
        Objects.requireNonNull(scheduleId, "スケジュールIDはnullにできません");
        Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        Objects.requireNonNull(changedDays, "変更日マップはnullにできません");
        Objects.requireNonNull(previousStatus, "変更前ステータスはnullにできません");
        Objects.requireNonNull(occurredAt, "イベント発生日時はnullにできません");

        // Mapの防御的コピー — イベントは不変であるため、外部からの変更を防止する
        changedDays = Collections.unmodifiableMap(new EnumMap<>(changedDays));
    }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>WeeklyScheduleのchangeAssignments()成功後に呼び出される。
     * イベント発生日時は自動的に現在時刻が設定される。</p>
     *
     * @param scheduleId     スケジュールID
     * @param employeeId     対象従業員ID
     * @param changedDays    変更後の曜日ごと割当
     * @param previousStatus 変更前のステータス
     * @return ShiftChangedEventインスタンス
     */
    public static ShiftChangedEvent of(
            ScheduleId scheduleId,
            EmployeeId employeeId,
            Map<DayOfWeek, ShiftPatternId> changedDays,
            ScheduleStatus previousStatus
    ) {
        return new ShiftChangedEvent(
                scheduleId,
                employeeId,
                changedDays,
                previousStatus,
                Instant.now()
        );
    }
}
