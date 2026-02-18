package com.example.kintai.attendance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * スケジュールイベントJPAエンティティ — weekly_schedule_eventsテーブルのマッピング
 *
 * <p>追記専用（INSERT ONLY）のイベントログ。
 * 4種類のスケジュールイベント（ASSIGNED, CHANGED, PUBLISHED, UNPUBLISHED）を
 * JSONB形式で記録する。イベントストアとして機能する。</p>
 */
@Entity
@Table(name = "weekly_schedule_events")
public class WeeklyScheduleEventJpaEntity {

    /** イベントID（UUID主キー） */
    @Id
    @Column(name = "id")
    private UUID id;

    /** 紐づく週次スケジュールID */
    @Column(name = "weekly_schedule_id", nullable = false)
    private UUID weeklyScheduleId;

    /** イベント種別（ASSIGNED, CHANGED, PUBLISHED, UNPUBLISHED） */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** イベントデータのJSON文字列（JSONB形式で保存） */
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    /** イベント発生日時 */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** イベント記録者ID（nullable） */
    @Column(name = "recorded_by")
    private UUID recordedBy;

    /** 作成日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 作成者 */
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    /** JPA必須の引数なしコンストラクタ */
    protected WeeklyScheduleEventJpaEntity() {}

    /**
     * 全フィールド指定コンストラクタ — リポジトリ実装から使用する
     */
    public WeeklyScheduleEventJpaEntity(
            UUID id, UUID weeklyScheduleId, String eventType, String payload,
            Instant occurredAt, UUID recordedBy, Instant createdAt, String createdBy
    ) {
        this.id = id;
        this.weeklyScheduleId = weeklyScheduleId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.recordedBy = recordedBy;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    // ゲッター

    public UUID getId() { return id; }
    public UUID getWeeklyScheduleId() { return weeklyScheduleId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getRecordedBy() { return recordedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
}
