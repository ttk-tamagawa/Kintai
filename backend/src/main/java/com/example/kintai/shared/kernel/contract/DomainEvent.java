package com.example.kintai.shared.kernel.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * ドメインイベント基底クラス — すべてのドメインイベントが継承する
 *
 * <p>共通フィールド:
 * <ul>
 *   <li>{@code eventId} — イベントを一意に識別するUUID（自動生成）</li>
 *   <li>{@code occurredAt} — イベント発生日時（自動設定）</li>
 * </ul>
 * </p>
 *
 * <p>サブクラスは {@link #getEventType()} を実装し、自身のイベント種別文字列を返す。
 * この値はイベントストアの {@code event_type} カラムに記録される。</p>
 *
 * <p>使用例:
 * <pre>{@code
 * public class ClockedInEvent extends DomainEvent {
 *     private final AttendanceRecordId attendanceRecordId;
 *     // ... 固有フィールド
 *
 *     @Override
 *     public String getEventType() {
 *         return "CLOCKED_IN";
 *     }
 * }
 * }</pre>
 * </p>
 */
public abstract class DomainEvent {

    /** イベントを一意に識別するUUID — インスタンス生成時に自動採番 */
    private final UUID eventId;

    /** イベント発生日時 — インスタンス生成時に自動設定 */
    private final Instant occurredAt;

    /**
     * コンストラクタ — eventId と occurredAt を自動設定する
     *
     * <p>サブクラスのコンストラクタから暗黙的に呼び出される。
     * eventId は UUID.randomUUID()、occurredAt は Instant.now() で自動生成される。</p>
     */
    protected DomainEvent() {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
    }

    /**
     * テスト・DB復元用コンストラクタ — eventId と occurredAt を外部から指定する
     *
     * <p>イベントストアからの復元やテスト時に使用する。
     * 通常のイベント生成ではデフォルトコンストラクタを使用すること。</p>
     *
     * @param eventId    イベントID
     * @param occurredAt イベント発生日時
     */
    protected DomainEvent(UUID eventId, Instant occurredAt) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
    }

    /**
     * イベント種別文字列を返す — イベントストアの event_type カラムに記録される
     *
     * <p>各サブクラスが自身のイベント種別を返すよう実装する。
     * 例: {@code "CLOCKED_IN"}, {@code "CLOCKED_OUT"}, {@code "ASSIGNED"} 等</p>
     *
     * @return イベント種別文字列
     */
    public abstract String getEventType();

    /**
     * イベントIDを取得する
     *
     * @return イベントを一意に識別するUUID
     */
    public UUID getEventId() {
        return eventId;
    }

    /**
     * イベント発生日時を取得する
     *
     * @return イベント発生日時
     */
    public Instant getOccurredAt() {
        return occurredAt;
    }
}
