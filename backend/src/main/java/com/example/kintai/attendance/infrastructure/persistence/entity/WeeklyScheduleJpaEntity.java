package com.example.kintai.attendance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 週次スケジュールJPAエンティティ — weekly_schedulesテーブルのマッピング
 *
 * <p>Write Model（書き込みモデル）のテーブル。
 * ドメインモデル（WeeklySchedule）の曜日別Map&lt;DayOfWeek, ShiftPatternId&gt;を
 * 7つの個別カラム（monday_pattern_id〜sunday_pattern_id）にマッピングする。
 * 楽観的ロック（@Version）でバージョン管理を行う。</p>
 */
@Entity
@Table(name = "weekly_schedules")
public class WeeklyScheduleJpaEntity {

    /** スケジュールID（UUID主キー） */
    @Id
    @Column(name = "id")
    private UUID id;

    /** 従業員ID */
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** 週の開始日（月曜日） */
    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    /** スケジュールステータス（DRAFT, PUBLISHED） */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 月曜日のシフトパターンID（nullは休み） */
    @Column(name = "monday_pattern_id")
    private UUID mondayPatternId;

    /** 火曜日のシフトパターンID（nullは休み） */
    @Column(name = "tuesday_pattern_id")
    private UUID tuesdayPatternId;

    /** 水曜日のシフトパターンID（nullは休み） */
    @Column(name = "wednesday_pattern_id")
    private UUID wednesdayPatternId;

    /** 木曜日のシフトパターンID（nullは休み） */
    @Column(name = "thursday_pattern_id")
    private UUID thursdayPatternId;

    /** 金曜日のシフトパターンID（nullは休み） */
    @Column(name = "friday_pattern_id")
    private UUID fridayPatternId;

    /** 土曜日のシフトパターンID（nullは休み） */
    @Column(name = "saturday_pattern_id")
    private UUID saturdayPatternId;

    /** 日曜日のシフトパターンID（nullは休み） */
    @Column(name = "sunday_pattern_id")
    private UUID sundayPatternId;

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
    protected WeeklyScheduleJpaEntity() {}

    /**
     * 全フィールド指定コンストラクタ — リポジトリ実装から使用する
     */
    public WeeklyScheduleJpaEntity(
            UUID id, UUID employeeId, LocalDate weekStartDate, String status,
            UUID mondayPatternId, UUID tuesdayPatternId, UUID wednesdayPatternId,
            UUID thursdayPatternId, UUID fridayPatternId, UUID saturdayPatternId,
            UUID sundayPatternId, int version, Instant createdAt, Instant updatedAt,
            String createdBy, String updatedBy
    ) {
        this.id = id;
        this.employeeId = employeeId;
        this.weekStartDate = weekStartDate;
        this.status = status;
        this.mondayPatternId = mondayPatternId;
        this.tuesdayPatternId = tuesdayPatternId;
        this.wednesdayPatternId = wednesdayPatternId;
        this.thursdayPatternId = thursdayPatternId;
        this.fridayPatternId = fridayPatternId;
        this.saturdayPatternId = saturdayPatternId;
        this.sundayPatternId = sundayPatternId;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    // ゲッター

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public String getStatus() { return status; }
    public UUID getMondayPatternId() { return mondayPatternId; }
    public UUID getTuesdayPatternId() { return tuesdayPatternId; }
    public UUID getWednesdayPatternId() { return wednesdayPatternId; }
    public UUID getThursdayPatternId() { return thursdayPatternId; }
    public UUID getFridayPatternId() { return fridayPatternId; }
    public UUID getSaturdayPatternId() { return saturdayPatternId; }
    public UUID getSundayPatternId() { return sundayPatternId; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getDeletedAt() { return deletedAt; }
}
