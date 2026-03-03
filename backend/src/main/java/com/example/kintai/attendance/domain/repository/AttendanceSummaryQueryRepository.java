package com.example.kintai.attendance.domain.repository;

import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
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
            String employeeId,
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
            int totalOvertimeMinutes,
            Instant updatedAt
    ) {}

    /**
     * 月次勤怠サマリー — monthly_attendance_summariesテーブルの読み取り結果
     */
    record MonthlySummary(
            UUID id,
            String employeeId,
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
     *
     * <p>departmentIdはdepartmentsテーブルのVARCHAR(36)に対応するためStringを使用する</p>
     */
    record DepartmentStats(
            String departmentId,
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

    /**
     * ページネーション結果 — コンテンツ+ページ情報を保持する汎用レコード
     *
     * @param content       現在ページのデータ
     * @param page          ページ番号（0始まり）
     * @param size          1ページあたりの件数
     * @param totalElements 全件数
     * @param totalPages    総ページ数
     */
    record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public PageResult {
            Objects.requireNonNull(content, "contentはnullにできません");
        }

        /** ページネーション結果を生成するファクトリメソッド */
        public static <T> PageResult<T> of(List<T> content, int page, int size, long totalElements) {
            int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
            return new PageResult<>(content, page, size, totalElements, totalPages);
        }
    }

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

    /**
     * 従業員IDと日付範囲で日次サマリーをページネーション付きで取得する
     *
     * @param employeeId    従業員ID
     * @param from          期間開始日
     * @param to            期間終了日
     * @param status        ステータスフィルタ（nullの場合は全ステータス）
     * @param page          ページ番号（0始まり）
     * @param size          1ページあたりの件数
     * @param sortField     ソートフィールド（workDate, totalOvertimeMinutes, status）
     * @param sortDirection ソート方向（asc, desc）
     * @return ページネーション結果
     */
    PageResult<DailySummary> findDailySummariesPaged(
            EmployeeId employeeId, LocalDate from, LocalDate to,
            String status, int page, int size, String sortField, String sortDirection);

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

    /**
     * 部門の月次サマリーをページネーション付きで取得する
     *
     * @param departmentId  部門ID
     * @param year          年
     * @param month         月
     * @param page          ページ番号（0始まり）
     * @param size          1ページあたりの件数
     * @param sortField     ソートフィールド（employeeName, totalWorkMinutes, totalOvertimeMinutes）
     * @param sortDirection ソート方向（asc, desc）
     * @return ページネーション結果
     */
    PageResult<MonthlySummary> findMonthlySummariesByDepartmentPaged(
            String departmentId, int year, int month,
            int page, int size, String sortField, String sortDirection);

    // ========================
    // 部門ダッシュボードクエリ
    // ========================

    /**
     * 指定年月の部門別統計を取得する
     */
    List<DepartmentStats> findDepartmentStats(int year, int month);
}
