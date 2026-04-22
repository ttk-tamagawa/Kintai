package com.example.kintai.attendance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * シフトパターンサマリー JPA エンティティ — shift_pattern_summaries テーブルのマッピング
 *
 * <p>Read Model（読み取りモデル）のテーブル。
 * Projector がドメインイベント（Defined / Deactivated / Reactivated）を購読して UPSERT する。
 * Write Model（shift_patterns）と独立した問い合わせ最適化テーブル。</p>
 *
 * <p>Read Model 側は並行書き込みが発生しない（Projector のみが書く）ため、
 * 楽観的ロック（@Version）は使用しない。</p>
 */
@Entity
@Table(name = "shift_pattern_summaries")
public class ShiftPatternSummaryJpaEntity {

    /** シフトパターンID（主キー、shift_patterns への FK） */
    @Id
    @Column(name = "shift_pattern_id")
    private UUID shiftPatternId;

    /** パターン名 */
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

    /** 夜勤フラグ */
    @Column(name = "is_overnight", nullable = false)
    private boolean isOvernight;

    /** 有効フラグ */
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    /** 最終イベント日時 */
    @Column(name = "last_event_at")
    private Instant lastEventAt;

    /** イベント数 */
    @Column(name = "event_count", nullable = false)
    private int eventCount;

    /** 作成日時（INSERT 時のみ設定、以降は更新しない） */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 更新日時 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 作成者（INSERT 時のみ設定、以降は更新しない） */
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    /** 更新者 */
    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    /** 論理削除日時（null なら有効） */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** JPA 必須の引数なしコンストラクタ */
    protected ShiftPatternSummaryJpaEntity() {}

    /**
     * Projector が使用する初期生成コンストラクタ
     *
     * <p>ShiftPatternDefinedEvent を初めて購読したときに新規行を作成する際に使用する。
     * 監査フィールド（createdAt / updatedAt）は現在時刻、作成者 / 更新者は "system" で初期化する。</p>
     *
     * @param shiftPatternId パターンID（主キー）
     * @param name           パターン名
     * @param startTime      勤務開始時刻
     * @param endTime        勤務終了時刻
     * @param breakMinutes   休憩時間（分）
     * @param isOvernight    夜勤フラグ
     */
    public ShiftPatternSummaryJpaEntity(
            UUID shiftPatternId,
            String name,
            LocalTime startTime,
            LocalTime endTime,
            int breakMinutes,
            boolean isOvernight
    ) {
        this.shiftPatternId = shiftPatternId;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = breakMinutes;
        this.isOvernight = isOvernight;
        this.isActive = true;
        this.eventCount = 0;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = "system";
        this.updatedBy = "system";
    }

    // ゲッター

    public UUID getShiftPatternId() { return shiftPatternId; }
    public String getName() { return name; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getBreakMinutes() { return breakMinutes; }
    public boolean isOvernight() { return isOvernight; }
    public boolean isActive() { return isActive; }
    public Instant getLastEventAt() { return lastEventAt; }
    public int getEventCount() { return eventCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    // Projector 用セッター（Read Model の更新に使用）

    /** 有効フラグを設定する */
    public void setActive(boolean isActive) { this.isActive = isActive; }
    /** 最終イベント日時を設定する */
    public void setLastEventAt(Instant lastEventAt) { this.lastEventAt = lastEventAt; }
    /** イベント数を設定する */
    public void setEventCount(int eventCount) { this.eventCount = eventCount; }
    /** 更新日時を設定する */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    /** 更新者を設定する */
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
