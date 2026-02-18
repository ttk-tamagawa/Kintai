package com.example.kintai.attendance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 週次スケジュールサマリーJPAエンティティ — weekly_schedule_summariesテーブルのマッピング
 *
 * <p>Read Model（読み取りモデル）のテーブル。
 * プロジェクターがドメインイベントを購読してUPSERTする。
 * 各曜日のパターンIDとパターン名を非正規化して保持するため、
 * JOINなしでカレンダービュー表示用データを取得できる。</p>
 */
@Entity
@Table(name = "weekly_schedule_summaries")
public class WeeklyScheduleSummaryJpaEntity {

    /** 週次スケジュールID（主キー、weekly_schedulesへのFK） */
    @Id
    @Column(name = "weekly_schedule_id")
    private UUID weeklyScheduleId;

    /** 従業員ID */
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** 週の開始日（月曜日） */
    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    /** スケジュールステータス */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 月曜日のシフトパターンID */
    @Column(name = "monday_pattern_id")
    private UUID mondayPatternId;

    /** 月曜日のシフトパターン名 */
    @Column(name = "monday_pattern_name", length = 20)
    private String mondayPatternName;

    /** 火曜日のシフトパターンID */
    @Column(name = "tuesday_pattern_id")
    private UUID tuesdayPatternId;

    /** 火曜日のシフトパターン名 */
    @Column(name = "tuesday_pattern_name", length = 20)
    private String tuesdayPatternName;

    /** 水曜日のシフトパターンID */
    @Column(name = "wednesday_pattern_id")
    private UUID wednesdayPatternId;

    /** 水曜日のシフトパターン名 */
    @Column(name = "wednesday_pattern_name", length = 20)
    private String wednesdayPatternName;

    /** 木曜日のシフトパターンID */
    @Column(name = "thursday_pattern_id")
    private UUID thursdayPatternId;

    /** 木曜日のシフトパターン名 */
    @Column(name = "thursday_pattern_name", length = 20)
    private String thursdayPatternName;

    /** 金曜日のシフトパターンID */
    @Column(name = "friday_pattern_id")
    private UUID fridayPatternId;

    /** 金曜日のシフトパターン名 */
    @Column(name = "friday_pattern_name", length = 20)
    private String fridayPatternName;

    /** 土曜日のシフトパターンID */
    @Column(name = "saturday_pattern_id")
    private UUID saturdayPatternId;

    /** 土曜日のシフトパターン名 */
    @Column(name = "saturday_pattern_name", length = 20)
    private String saturdayPatternName;

    /** 日曜日のシフトパターンID */
    @Column(name = "sunday_pattern_id")
    private UUID sundayPatternId;

    /** 日曜日のシフトパターン名 */
    @Column(name = "sunday_pattern_name", length = 20)
    private String sundayPatternName;

    /** シフト割当日数 */
    @Column(name = "assigned_days", nullable = false)
    private int assignedDays;

    /** 最終イベント日時 */
    @Column(name = "last_event_at")
    private Instant lastEventAt;

    /** イベント数 */
    @Column(name = "event_count", nullable = false)
    private int eventCount;

    /** 作成日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 更新日時 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 作成者 */
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    /** 更新者 */
    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    /** 論理削除日時 */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** JPA必須の引数なしコンストラクタ */
    protected WeeklyScheduleSummaryJpaEntity() {}

    /**
     * プロジェクターが使用する初期生成コンストラクタ
     *
     * <p>シフト割当イベントで初めてサマリーを作成する際に使用する。
     * 曜日別パターンはnull（未割当）、割当日数はゼロで初期化する。</p>
     *
     * @param weeklyScheduleId スケジュールID（主キー）
     * @param employeeId       従業員ID
     * @param weekStartDate    週の開始日（月曜日）
     * @param status           スケジュールステータス
     */
    public WeeklyScheduleSummaryJpaEntity(UUID weeklyScheduleId, UUID employeeId,
                                           LocalDate weekStartDate, String status) {
        this.weeklyScheduleId = weeklyScheduleId;
        this.employeeId = employeeId;
        this.weekStartDate = weekStartDate;
        this.status = status;
        this.assignedDays = 0;
        this.eventCount = 0;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = "system";
        this.updatedBy = "system";
    }

    // ゲッター

    public UUID getWeeklyScheduleId() { return weeklyScheduleId; }
    public UUID getEmployeeId() { return employeeId; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public String getStatus() { return status; }
    public UUID getMondayPatternId() { return mondayPatternId; }
    public String getMondayPatternName() { return mondayPatternName; }
    public UUID getTuesdayPatternId() { return tuesdayPatternId; }
    public String getTuesdayPatternName() { return tuesdayPatternName; }
    public UUID getWednesdayPatternId() { return wednesdayPatternId; }
    public String getWednesdayPatternName() { return wednesdayPatternName; }
    public UUID getThursdayPatternId() { return thursdayPatternId; }
    public String getThursdayPatternName() { return thursdayPatternName; }
    public UUID getFridayPatternId() { return fridayPatternId; }
    public String getFridayPatternName() { return fridayPatternName; }
    public UUID getSaturdayPatternId() { return saturdayPatternId; }
    public String getSaturdayPatternName() { return saturdayPatternName; }
    public UUID getSundayPatternId() { return sundayPatternId; }
    public String getSundayPatternName() { return sundayPatternName; }
    public int getAssignedDays() { return assignedDays; }
    public Instant getLastEventAt() { return lastEventAt; }
    public int getEventCount() { return eventCount; }

    // プロジェクター用セッター（Read Modelの更新に使用）

    /** ステータスを設定する */
    public void setStatus(String status) { this.status = status; }
    /** 月曜日のパターンIDを設定する */
    public void setMondayPatternId(UUID v) { this.mondayPatternId = v; }
    /** 月曜日のパターン名を設定する */
    public void setMondayPatternName(String v) { this.mondayPatternName = v; }
    /** 火曜日のパターンIDを設定する */
    public void setTuesdayPatternId(UUID v) { this.tuesdayPatternId = v; }
    /** 火曜日のパターン名を設定する */
    public void setTuesdayPatternName(String v) { this.tuesdayPatternName = v; }
    /** 水曜日のパターンIDを設定する */
    public void setWednesdayPatternId(UUID v) { this.wednesdayPatternId = v; }
    /** 水曜日のパターン名を設定する */
    public void setWednesdayPatternName(String v) { this.wednesdayPatternName = v; }
    /** 木曜日のパターンIDを設定する */
    public void setThursdayPatternId(UUID v) { this.thursdayPatternId = v; }
    /** 木曜日のパターン名を設定する */
    public void setThursdayPatternName(String v) { this.thursdayPatternName = v; }
    /** 金曜日のパターンIDを設定する */
    public void setFridayPatternId(UUID v) { this.fridayPatternId = v; }
    /** 金曜日のパターン名を設定する */
    public void setFridayPatternName(String v) { this.fridayPatternName = v; }
    /** 土曜日のパターンIDを設定する */
    public void setSaturdayPatternId(UUID v) { this.saturdayPatternId = v; }
    /** 土曜日のパターン名を設定する */
    public void setSaturdayPatternName(String v) { this.saturdayPatternName = v; }
    /** 日曜日のパターンIDを設定する */
    public void setSundayPatternId(UUID v) { this.sundayPatternId = v; }
    /** 日曜日のパターン名を設定する */
    public void setSundayPatternName(String v) { this.sundayPatternName = v; }
    /** シフト割当日数を設定する */
    public void setAssignedDays(int assignedDays) { this.assignedDays = assignedDays; }
    /** 最終イベント日時を設定する */
    public void setLastEventAt(Instant lastEventAt) { this.lastEventAt = lastEventAt; }
    /** イベント数を設定する */
    public void setEventCount(int eventCount) { this.eventCount = eventCount; }
    /** 更新日時を設定する */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    /** 更新者を設定する */
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
