package com.example.kintai.attendance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 日次勤怠サマリーJPAエンティティ — attendance_summariesテーブルのマッピング
 *
 * <p>Read Model（読み取りモデル）のテーブル。
 * プロジェクターがドメインイベントを購読してUPSERTする。
 * クエリリポジトリが画面表示用データとして参照する。</p>
 */
@Entity
@Table(name = "attendance_summaries")
public class AttendanceSummaryJpaEntity {

    /** 勤怠記録ID（主キー、attendancesへのFK） */
    @Id
    @Column(name = "attendance_id")
    private UUID attendanceId;

    /** 従業員ID */
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** 勤務日 */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** 勤怠ステータス */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 出勤時刻（nullable: 未出勤の場合） */
    @Column(name = "clock_in_time")
    private Instant clockInTime;

    /** 退勤時刻（nullable: 未退勤の場合） */
    @Column(name = "clock_out_time")
    private Instant clockOutTime;

    /** 所定労働時間（分） */
    @Column(name = "scheduled_minutes", nullable = false)
    private int scheduledMinutes;

    /** 実労働時間（分） */
    @Column(name = "actual_minutes", nullable = false)
    private int actualMinutes;

    /** 休憩時間（分） */
    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    /** 正味労働時間（分） — 実労働 - 休憩 */
    @Column(name = "net_work_minutes", nullable = false)
    private int netWorkMinutes;

    /** 普通残業時間（分） */
    @Column(name = "regular_overtime_minutes", nullable = false)
    private int regularOvertimeMinutes;

    /** 深夜勤務時間（分） */
    @Column(name = "late_night_minutes", nullable = false)
    private int lateNightMinutes;

    /** 休日勤務時間（分） */
    @Column(name = "holiday_minutes", nullable = false)
    private int holidayMinutes;

    /** 残業合計時間（分） */
    @Column(name = "total_overtime_minutes", nullable = false)
    private int totalOvertimeMinutes;

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
    protected AttendanceSummaryJpaEntity() {}

    /**
     * プロジェクターが使用する初期生成コンストラクタ
     *
     * <p>出勤打刻・手動勤務登録で初めてサマリーを作成する際に使用する。
     * 数値フィールドはゼロ、ステータスはNOT_CLOCKEDで初期化する。</p>
     *
     * @param attendanceId 勤怠記録ID（主キー）
     * @param employeeId   従業員ID
     * @param workDate     勤務日
     */
    public AttendanceSummaryJpaEntity(UUID attendanceId, UUID employeeId, LocalDate workDate) {
        this.attendanceId = attendanceId;
        this.employeeId = employeeId;
        this.workDate = workDate;
        this.status = "NOT_CLOCKED";
        this.scheduledMinutes = 0;
        this.actualMinutes = 0;
        this.breakMinutes = 0;
        this.netWorkMinutes = 0;
        this.regularOvertimeMinutes = 0;
        this.lateNightMinutes = 0;
        this.holidayMinutes = 0;
        this.totalOvertimeMinutes = 0;
        this.eventCount = 0;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = "system";
        this.updatedBy = "system";
    }

    // ゲッター

    public UUID getAttendanceId() { return attendanceId; }
    public UUID getEmployeeId() { return employeeId; }
    public LocalDate getWorkDate() { return workDate; }
    public String getStatus() { return status; }
    public Instant getClockInTime() { return clockInTime; }
    public Instant getClockOutTime() { return clockOutTime; }
    public int getScheduledMinutes() { return scheduledMinutes; }
    public int getActualMinutes() { return actualMinutes; }
    public int getBreakMinutes() { return breakMinutes; }
    public int getNetWorkMinutes() { return netWorkMinutes; }
    public int getRegularOvertimeMinutes() { return regularOvertimeMinutes; }
    public int getLateNightMinutes() { return lateNightMinutes; }
    public int getHolidayMinutes() { return holidayMinutes; }
    public int getTotalOvertimeMinutes() { return totalOvertimeMinutes; }
    public Instant getLastEventAt() { return lastEventAt; }
    public int getEventCount() { return eventCount; }

    // プロジェクター用セッター（Read Modelの更新に使用）

    /** 勤怠ステータスを設定する */
    public void setStatus(String status) { this.status = status; }
    /** 出勤時刻を設定する */
    public void setClockInTime(Instant clockInTime) { this.clockInTime = clockInTime; }
    /** 退勤時刻を設定する */
    public void setClockOutTime(Instant clockOutTime) { this.clockOutTime = clockOutTime; }
    /** 所定労働時間（分）を設定する */
    public void setScheduledMinutes(int scheduledMinutes) { this.scheduledMinutes = scheduledMinutes; }
    /** 実労働時間（分）を設定する */
    public void setActualMinutes(int actualMinutes) { this.actualMinutes = actualMinutes; }
    /** 休憩時間（分）を設定する */
    public void setBreakMinutes(int breakMinutes) { this.breakMinutes = breakMinutes; }
    /** 正味労働時間（分）を設定する */
    public void setNetWorkMinutes(int netWorkMinutes) { this.netWorkMinutes = netWorkMinutes; }
    /** 普通残業時間（分）を設定する */
    public void setRegularOvertimeMinutes(int v) { this.regularOvertimeMinutes = v; }
    /** 深夜勤務時間（分）を設定する */
    public void setLateNightMinutes(int lateNightMinutes) { this.lateNightMinutes = lateNightMinutes; }
    /** 休日勤務時間（分）を設定する */
    public void setHolidayMinutes(int holidayMinutes) { this.holidayMinutes = holidayMinutes; }
    /** 残業合計時間（分）を設定する */
    public void setTotalOvertimeMinutes(int v) { this.totalOvertimeMinutes = v; }
    /** 最終イベント日時を設定する */
    public void setLastEventAt(Instant lastEventAt) { this.lastEventAt = lastEventAt; }
    /** イベント数を設定する */
    public void setEventCount(int eventCount) { this.eventCount = eventCount; }
    /** 更新日時を設定する */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    /** 更新者を設定する */
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
