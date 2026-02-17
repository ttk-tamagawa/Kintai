package com.example.kintai.attendance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 打刻エントリJPAエンティティ — clock_entriesテーブルのマッピング
 *
 * <p>追記専用（INSERT ONLY）のテーブル。
 * 一度記録された打刻は変更・削除しない。
 * 打刻修正時はsource=CORRECTIONの新しい行が追加される。</p>
 */
@Entity
@Table(name = "clock_entries")
public class ClockEntryJpaEntity {

    /** 打刻エントリID（UUID主キー） */
    @Id
    @Column(name = "id")
    private UUID id;

    /** 紐づく勤怠記録ID */
    @Column(name = "attendance_id", nullable = false)
    private UUID attendanceId;

    /** 打刻種別（CLOCK_IN, CLOCK_OUT, BREAK_START, BREAK_END） */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** 打刻時刻 */
    @Column(name = "time", nullable = false)
    private Instant time;

    /** 打刻元（WEB, MOBILE, MANUAL, CORRECTION） */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    /** 修正元の打刻ID（修正の場合のみ設定） */
    @Column(name = "correction_id")
    private UUID correctionId;

    /** 作成日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 作成者 */
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    /** JPA必須の引数なしコンストラクタ */
    protected ClockEntryJpaEntity() {}

    /**
     * 全フィールド指定コンストラクタ — リポジトリ実装から使用する
     */
    public ClockEntryJpaEntity(
            UUID id, UUID attendanceId, String type, Instant time,
            String source, UUID correctionId, Instant createdAt, String createdBy
    ) {
        this.id = id;
        this.attendanceId = attendanceId;
        this.type = type;
        this.time = time;
        this.source = source;
        this.correctionId = correctionId;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    // ゲッター

    public UUID getId() { return id; }
    public UUID getAttendanceId() { return attendanceId; }
    public String getType() { return type; }
    public Instant getTime() { return time; }
    public String getSource() { return source; }
    public UUID getCorrectionId() { return correctionId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
}
