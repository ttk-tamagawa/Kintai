package com.example.kintai.attendance.domain.model.shift.event;

import com.example.kintai.attendance.domain.model.shift.PatternName;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * シフトパターン無効化イベント — 既存のシフトパターンが無効化されたときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（shift_pattern_summaries）の is_active フラグを false に更新</li>
 *   <li>監査ログへの無効化記録</li>
 * </ul>
 * </p>
 */
public class ShiftPatternDeactivatedEvent extends DomainEvent {

    /** シフトパターンID */
    private final ShiftPatternId patternId;
    /** パターン名（監査・ログ用） */
    private final PatternName name;

    /**
     * コンストラクタ — 固有フィールドのnullチェックを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public ShiftPatternDeactivatedEvent(
            ShiftPatternId patternId,
            PatternName name
    ) {
        super();
        this.patternId = Objects.requireNonNull(patternId, "シフトパターンIDはnullにできません");
        this.name = Objects.requireNonNull(name, "パターン名はnullにできません");
    }

    @Override
    public String getEventType() {
        return "SHIFT_PATTERN_DEACTIVATED";
    }

    public ShiftPatternId patternId() { return patternId; }
    public PatternName name() { return name; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>ShiftPatternのdeactivate()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static ShiftPatternDeactivatedEvent of(
            ShiftPatternId patternId,
            PatternName name
    ) {
        return new ShiftPatternDeactivatedEvent(patternId, name);
    }
}
