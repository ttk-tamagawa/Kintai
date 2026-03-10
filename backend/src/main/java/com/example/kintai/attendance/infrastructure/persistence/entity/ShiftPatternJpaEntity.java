package com.example.kintai.attendance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * シフトパターンJPAエンティティ — shift_patternsテーブルのマッピング
 *
 * <p>Write Model（書き込みモデル）のテーブル。
 * ドメインモデル（ShiftPattern）とDBの間の橋渡し役。
 * 楽観的ロック（@Version）でバージョン管理を行う。</p>
 */
@Entity
@Table(name = "shift_patterns")
public class ShiftPatternJpaEntity {

    /** シフトパターンID（UUID主キー） */
    @Id
    @Column(name = "id")
    private UUID id;

    /** パターン名（システム全体で一意） */
    @Column(name = "name", nullable = false, length = 20)
    private String name;

    /** 勤務開始時刻 */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** 勤務終了時刻 */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** 休憩時間（分） */
    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    /** 夜勤フラグ — trueの場合、日跨ぎを許容する */
    @Column(name = "is_overnight", nullable = false)
    private boolean isOvernight;

    /** 有効フラグ — falseの場合、新規割当に使用不可 */
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    /** 楽観的ロック用バージョン — JPAが自動インクリメントする */
    @Version
    @Column(name = "version", nullable = false)
    private int version;

    /** 作成日時（INSERT時のみ設定、以降は更新しない） */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 更新日時 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 作成者（INSERT時のみ設定、以降は更新しない） */
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    /** 更新者 */
    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    /** 論理削除日時（nullなら有効） */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** JPA必須の引数なしコンストラクタ */
    protected ShiftPatternJpaEntity() {}

    /**
     * 全フィールド指定コンストラクタ — リポジトリ実装から使用する
     */
    public ShiftPatternJpaEntity(
            UUID id, String name, LocalTime startTime, LocalTime endTime,
            int breakMinutes, boolean isOvernight, boolean isActive,
            int version, Instant createdAt, Instant updatedAt,
            String createdBy, String updatedBy
    ) {
        this.id = id;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = breakMinutes;
        this.isOvernight = isOvernight;
        this.isActive = isActive;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    // ゲッター

    public UUID getId() { return id; }
    public String getName() { return name; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getBreakMinutes() { return breakMinutes; }
    public boolean isOvernight() { return isOvernight; }
    public boolean isActive() { return isActive; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getDeletedAt() { return deletedAt; }
}
