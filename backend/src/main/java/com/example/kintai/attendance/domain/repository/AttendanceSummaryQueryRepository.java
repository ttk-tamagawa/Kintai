package com.example.kintai.attendance.domain.repository;

import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 勤怠サマリークエリリポジトリ — Read Model参照用インターフェース
 *
 * <p>CQRS（コマンドクエリ責務分離）の読み取り側を担当する。
 * 日次勤怠一覧、月次サマリー、部門ダッシュボードのデータを提供する。
 * Write Model（集約）とは独立したRead Modelテーブルから読み取る。</p>
 */
public interface AttendanceSummaryQueryRepository {

    // ========================
    // Read Model DTO定義
    // ========================

    /**
     * 日次勤怠サマリー — attendance_summariesテーブルの読み取り結果
     */
    record DailySummary(
            UUID attendanceId,
            UUID employeeId,
            LocalDate workDate,
            String status,
            Instant clockInTime,
            Instant clockOutTime,
            int scheduledMinutes,
            int actualMinutes,
            int breakMinutes,
            int netWorkMinutes,
            int regularOvertimeMinutes,
            int lateNightMinutes,
            int holidayMinutes,
            int totalOvertimeMinutes
    ) {}

    /**
     * 月次勤怠サマリー — monthly_attendance_summariesテーブルの読み取り結果
     */
    record MonthlySummary(
            UUID id,
            UUID employeeId,
            String employeeName,
            String departmentId,
            int year,
            int month,
            int totalWorkDays,
            int totalWorkMinutes,
            int totalOvertimeMinutes,
            int totalLateNightMinutes,
            int totalHolidayMinutes,
            int totalBreakMinutes,
            BigDecimal paidLeaveUsed
    ) {}

    /**
     * 部門別勤怠統計 — department_attendance_statsマテリアライズドビューの読み取り結果
     */
    record DepartmentStats(
            UUID departmentId,
            String departmentName,
            int year,
            int month,
            int totalEmployees,
            BigDecimal avgWorkMinutes,
            BigDecimal avgOvertimeMinutes,
            int maxOvertimeMinutes,
            int totalOvertimeMinutes,
            BigDecimal avgLateNightMinutes,
            int overtimeAlertCount,
            int missingClockCount,
            BigDecimal attendanceRate
    ) {}

    // ========================
    // 日次勤怠クエリ
    // ========================

    /**
     * 勤怠記録IDで日次サマリーを取得する
     */
    Optional<DailySummary> findDailySummaryByAttendanceId(AttendanceRecordId attendanceId);

    /**
     * 従業員IDと日付範囲で日次サマリー一覧を取得する
     */
    List<DailySummary> findDailySummaries(EmployeeId employeeId, LocalDate from, LocalDate to);

    // ========================
    // 月次サマリークエリ
    // ========================

    /**
     * 従業員の月次サマリーを取得する
     */
    Optional<MonthlySummary> findMonthlySummary(EmployeeId employeeId, int year, int month);

    /**
     * 部門の月次サマリー一覧を取得する
     */
    List<MonthlySummary> findMonthlySummariesByDepartment(String departmentId, int year, int month);

    // ========================
    // 部門ダッシュボードクエリ
    // ========================

    /**
     * 指定年月の部門別統計を取得する
     */
    List<DepartmentStats> findDepartmentStats(int year, int month);
}
