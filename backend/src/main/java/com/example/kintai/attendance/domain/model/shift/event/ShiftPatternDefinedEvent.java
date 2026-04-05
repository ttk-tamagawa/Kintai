package com.example.kintai.attendance.domain.model.shift.event;

import com.example.kintai.attendance.domain.model.shift.PatternName;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;

/**
 * シフトパターン定義イベント — 新しいシフトパターンが作成されたときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（weekly_schedule_summaries）のパターン名マスタを更新</li>
 *   <li>管理者への作成完了通知</li>
 * </ul>
 * </p>
 */
public class ShiftPatternDefinedEvent extends DomainEvent {

    /** シフトパターンID */
    private final ShiftPatternId patternId;
    /** パターン名（早番、遅番等） */
    private final PatternName name;
    /** 勤務開始時刻 */
    private final LocalTime startTime;
    /** 勤務終了時刻 */
    private final LocalTime endTime;
    /** 夜勤フラグ（trueなら日跨ぎパターン） */
    private final boolean isOvernight;

    /**
     * コンストラクタ — 固有フィールドのnullチェックを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public ShiftPatternDefinedEvent(
            ShiftPatternId patternId,
            PatternName name,
            LocalTime startTime,
            LocalTime endTime,
            boolean isOvernight
    ) {
        super();
        this.patternId = Objects.requireNonNull(patternId, "シフトパターンIDはnullにできません");
        this.name = Objects.requireNonNull(name, "パターン名はnullにできません");
        this.startTime = Objects.requireNonNull(startTime, "勤務開始時刻はnullにできません");
        this.endTime = Objects.requireNonNull(endTime, "勤務終了時刻はnullにできません");
        this.isOvernight = isOvernight;
    }

    @Override
    public String getEventType() {
        return "SHIFT_PATTERN_DEFINED";
    }

    public ShiftPatternId patternId() { return patternId; }
    public PatternName name() { return name; }
    public LocalTime startTime() { return startTime; }
    public LocalTime endTime() { return endTime; }
    public boolean isOvernight() { return isOvernight; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>ShiftPatternのdefine()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static ShiftPatternDefinedEvent of(
            ShiftPatternId patternId,
            PatternName name,
            LocalTime startTime,
            LocalTime endTime,
            boolean isOvernight
    ) {
        return new ShiftPatternDefinedEvent(patternId, name, startTime, endTime, isOvernight);
    }
}
