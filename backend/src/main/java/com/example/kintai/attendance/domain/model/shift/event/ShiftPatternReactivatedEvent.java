package com.example.kintai.attendance.domain.model.shift.event;

import com.example.kintai.attendance.domain.model.shift.PatternName;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * シフトパターン再有効化イベント — 無効化されたシフトパターンが再有効化されたときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（shift_pattern_summaries）の is_active フラグを true に更新</li>
 *   <li>監査ログへの再有効化記録</li>
 * </ul>
 * </p>
 */
public class ShiftPatternReactivatedEvent extends DomainEvent {

    /** シフトパターンID */
    private final ShiftPatternId patternId;
    /** パターン名（監査・ログ用） */
    private final PatternName name;

    /**
     * コンストラクタ — 固有フィールドのnullチェックを行い、基底クラスで eventId・occurredAt を自動設定する
     */
    public ShiftPatternReactivatedEvent(
            ShiftPatternId patternId,
            PatternName name
    ) {
        super();
        this.patternId = Objects.requireNonNull(patternId, "シフトパターンIDはnullにできません");
        this.name = Objects.requireNonNull(name, "パターン名はnullにできません");
    }

    @Override
    public String getEventType() {
        return "SHIFT_PATTERN_REACTIVATED";
    }

    public ShiftPatternId patternId() { return patternId; }
    public PatternName name() { return name; }
    public Instant occurredAt() { return getOccurredAt(); }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>ShiftPatternのreactivate()成功後に呼び出される。
     * eventId・occurredAt は基底クラスで自動設定される。</p>
     */
    public static ShiftPatternReactivatedEvent of(
            ShiftPatternId patternId,
            PatternName name
    ) {
        return new ShiftPatternReactivatedEvent(patternId, name);
    }
}
