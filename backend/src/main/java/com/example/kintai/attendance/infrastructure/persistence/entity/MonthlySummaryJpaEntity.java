package com.example.kintai.attendance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 月次勤怠サマリーJPAエンティティ — monthly_attendance_summariesテーブルのマッピング
 *
 * <p>Read Model（読み取りモデル）のテーブル。
 * 日次サマリー変更時にプロジェクターが月次集計を更新する。
 * employee_name等を非正規化して保持し、表示用クエリの高速化を図る。</p>
 */
@Entity
@Table(name = "monthly_attendance_summaries")
public class MonthlySummaryJpaEntity {

    /** 月次サマリーID（UUID主キー） */
    @Id
    @Column(name = "id")
    private UUID id;

    /** 従業員ID */
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** 従業員名（非正規化: 表示用） */
    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    /** 部門ID */
    @Column(name = "department_id", nullable = false, length = 36)
    private String departmentId;

    /** 年 */
    @Column(name = "year", nullable = false)
    private short year;

    /** 月（1〜12） */
    @Column(name = "month", nullable = false)
    private short month;

    /** 出勤日数 */
    @Column(name = "total_work_days", nullable = false)
    private int totalWorkDays;

    /** 総勤務時間（分） */
    @Column(name = "total_work_minutes", nullable = false)
    private int totalWorkMinutes;

    /** 総残業時間（分） */
    @Column(name = "total_overtime_minutes", nullable = false)
    private int totalOvertimeMinutes;

    /** 総深夜勤務時間（分） */
    @Column(name = "total_late_night_minutes", nullable = false)
    private int totalLateNightMinutes;

    /** 総休日勤務時間（分） */
    @Column(name = "total_holiday_minutes", nullable = false)
    private int totalHolidayMinutes;

    /** 総休憩時間（分） */
    @Column(name = "total_break_minutes", nullable = false)
    private int totalBreakMinutes;

    /** 有給使用日数（0.5日刻み対応） */
    @Column(name = "paid_leave_used", nullable = false, precision = 5, scale = 1)
    private BigDecimal paidLeaveUsed;

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

    /** JPA必須の引数なしコンストラクタ */
    protected MonthlySummaryJpaEntity() {}

    // ゲッター

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getDepartmentId() { return departmentId; }
    public short getYear() { return year; }
    public short getMonth() { return month; }
    public int getTotalWorkDays() { return totalWorkDays; }
    public int getTotalWorkMinutes() { return totalWorkMinutes; }
    public int getTotalOvertimeMinutes() { return totalOvertimeMinutes; }
    public int getTotalLateNightMinutes() { return totalLateNightMinutes; }
    public int getTotalHolidayMinutes() { return totalHolidayMinutes; }
    public int getTotalBreakMinutes() { return totalBreakMinutes; }
    public BigDecimal getPaidLeaveUsed() { return paidLeaveUsed; }
}
