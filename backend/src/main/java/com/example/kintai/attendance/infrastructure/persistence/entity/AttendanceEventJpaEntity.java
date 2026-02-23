package com.example.kintai.attendance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 勤怠イベントJPAエンティティ — attendance_eventsテーブルのマッピング
 *
 * <p>追記専用（INSERT ONLY）のイベントログ。
 * 8種類のドメインイベントをJSONB形式で記録する。
 * イベントソーシングの「イベントストア」として機能する。</p>
 */
@Entity
@Table(name = "attendance_events")
public class AttendanceEventJpaEntity {

    /** イベントID（UUID主キー） */
    @Id
    @Column(name = "id")
    private UUID id;

    /** 紐づく勤怠記録ID */
    @Column(name = "attendance_id", nullable = false)
    private UUID attendanceId;

    /** イベント種別（CLOCKED_IN, CLOCKED_OUT, BREAK_STARTED 等8種類） */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** イベントデータのJSON文字列（JSONB形式で保存） */
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
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
    protected AttendanceEventJpaEntity() {}

    /**
     * 全フィールド指定コンストラクタ — リポジトリ実装から使用する
     */
    public AttendanceEventJpaEntity(
            UUID id, UUID attendanceId, String eventType, String payload,
            Instant occurredAt, UUID recordedBy, Instant createdAt, String createdBy
    ) {
        this.id = id;
        this.attendanceId = attendanceId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.recordedBy = recordedBy;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    // ゲッター

    public UUID getId() { return id; }
    public UUID getAttendanceId() { return attendanceId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getRecordedBy() { return recordedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
}
