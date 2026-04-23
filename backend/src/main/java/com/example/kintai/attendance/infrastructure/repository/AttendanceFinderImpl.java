package com.example.kintai.attendance.infrastructure.repository;

import com.example.kintai.attendance.application.query.AttendanceFinder;
import com.example.kintai.attendance.infrastructure.jooq.generated.tables.records.AttendanceSummariesRecord;
import com.example.kintai.attendance.infrastructure.jooq.generated.tables.records.DepartmentAttendanceStatsRecord;
import com.example.kintai.attendance.infrastructure.jooq.generated.tables.records.MonthlyAttendanceSummariesRecord;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.kintai.attendance.infrastructure.jooq.generated.Tables.ATTENDANCE_SUMMARIES;
import static com.example.kintai.attendance.infrastructure.jooq.generated.Tables.DEPARTMENT_ATTENDANCE_STATS;
import static com.example.kintai.attendance.infrastructure.jooq.generated.Tables.MONTHLY_ATTENDANCE_SUMMARIES;

/**
 * 勤怠ファインダー実装 — Read Model 参照用
 *
 * <p>CQRS（コマンドクエリ責務分離）の読み取り側実装。
 * attendance_summaries、monthly_attendance_summaries、
 * department_attendance_stats（マテリアライズドビュー）のデータを取得する。
 * jOOQ DSL で型安全に SQL を組み立てる。</p>
 */
@Repository
@Transactional(readOnly = true)
public class AttendanceFinderImpl implements AttendanceFinder {

    /** 日次サマリーのソートフィールド → jOOQ Field のマッピング（SQLインジェクション防止） */
    private static final Map<String, Field<?>> DAILY_SORT_FIELDS = Map.of(
            "workDate", ATTENDANCE_SUMMARIES.WORK_DATE,
            "totalOvertimeMinutes", ATTENDANCE_SUMMARIES.TOTAL_OVERTIME_MINUTES,
            "overtimeMinutes", ATTENDANCE_SUMMARIES.TOTAL_OVERTIME_MINUTES,
            "status", ATTENDANCE_SUMMARIES.STATUS
    );

    /** 月次サマリーのソートフィールド → jOOQ Field のマッピング */
    private static final Map<String, Field<?>> MONTHLY_SORT_FIELDS = Map.of(
            "employeeName", MONTHLY_ATTENDANCE_SUMMARIES.EMPLOYEE_NAME,
            "totalWorkMinutes", MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_WORK_MINUTES,
            "totalWorkHours", MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_WORK_MINUTES,
            "totalOvertimeMinutes", MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_OVERTIME_MINUTES,
            "totalOvertimeHours", MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_OVERTIME_MINUTES
    );

    /** jOOQ DSLContext — 型安全な SQL アクセス */
    private final DSLContext dsl;

    public AttendanceFinderImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ========================
    // 日次サマリークエリ
    // ========================

    @Override
    public Optional<DailySummary> findDailySummaryByAttendanceId(AttendanceRecordId attendanceId) {
        // attendance_summaries テーブルから主キーで検索する
        return dsl.selectFrom(ATTENDANCE_SUMMARIES)
                .where(ATTENDANCE_SUMMARIES.ATTENDANCE_ID.eq(attendanceId.value()))
                .fetchOptional()
                .map(this::toDailySummary);
    }

    @Override
    public List<DailySummary> findDailySummaries(EmployeeId employeeId, LocalDate from, LocalDate to) {
        // 従業員IDと日付範囲で日次サマリーを取得（日付降順、論理削除を除外）
        return dsl.selectFrom(ATTENDANCE_SUMMARIES)
                .where(ATTENDANCE_SUMMARIES.EMPLOYEE_ID.eq(employeeId.value()))
                .and(ATTENDANCE_SUMMARIES.WORK_DATE.between(from, to))
                .and(ATTENDANCE_SUMMARIES.DELETED_AT.isNull())
                .orderBy(ATTENDANCE_SUMMARIES.WORK_DATE.desc())
                .fetch()
                .map(this::toDailySummary);
    }

    @Override
    public PageResult<DailySummary> findDailySummariesPaged(
            EmployeeId employeeId, LocalDate from, LocalDate to,
            String status, int page, int size, String sortField, String sortDirection) {

        // ソートフィールドをホワイトリストで検証する（SQLインジェクション防止）
        Field<?> sortColumn = DAILY_SORT_FIELDS.getOrDefault(sortField, ATTENDANCE_SUMMARIES.WORK_DATE);
        SortField<?> orderBy = "asc".equalsIgnoreCase(sortDirection) ? sortColumn.asc() : sortColumn.desc();

        // WHERE 条件を合成する（statusがnullの場合は全ステータス対象）
        Condition condition = ATTENDANCE_SUMMARIES.EMPLOYEE_ID.eq(employeeId.value())
                .and(ATTENDANCE_SUMMARIES.WORK_DATE.between(from, to))
                .and(ATTENDANCE_SUMMARIES.DELETED_AT.isNull());
        if (status != null && !status.isEmpty()) {
            condition = condition.and(ATTENDANCE_SUMMARIES.STATUS.eq(status));
        }

        // 全件数を取得する（ページネーション情報の算出用）
        long totalElements = dsl.selectCount()
                .from(ATTENDANCE_SUMMARIES)
                .where(condition)
                .fetchOne(0, long.class);

        // データを取得する（ページネーション + ソート付き）
        List<DailySummary> content = dsl.selectFrom(ATTENDANCE_SUMMARIES)
                .where(condition)
                .orderBy(orderBy)
                .limit(size)
                .offset(page * size)
                .fetch()
                .map(this::toDailySummary);

        return PageResult.of(content, page, size, totalElements);
    }

    // ========================
    // 月次サマリークエリ
    // ========================

    @Override
    public Optional<MonthlySummary> findMonthlySummary(EmployeeId employeeId, int year, int month) {
        // employee_id + year + month のユニーク制約で 1 件取得する
        return dsl.selectFrom(MONTHLY_ATTENDANCE_SUMMARIES)
                .where(MONTHLY_ATTENDANCE_SUMMARIES.EMPLOYEE_ID.eq(employeeId.value()))
                .and(MONTHLY_ATTENDANCE_SUMMARIES.YEAR.eq((short) year))
                .and(MONTHLY_ATTENDANCE_SUMMARIES.MONTH.eq((short) month))
                .fetchOptional()
                .map(this::toMonthlySummary);
    }

    @Override
    public List<MonthlySummary> findMonthlySummariesByDepartment(String departmentId, int year, int month) {
        // 部門の全従業員の月次サマリーを取得（従業員名順）
        return dsl.selectFrom(MONTHLY_ATTENDANCE_SUMMARIES)
                .where(MONTHLY_ATTENDANCE_SUMMARIES.DEPARTMENT_ID.eq(departmentId))
                .and(MONTHLY_ATTENDANCE_SUMMARIES.YEAR.eq((short) year))
                .and(MONTHLY_ATTENDANCE_SUMMARIES.MONTH.eq((short) month))
                .orderBy(MONTHLY_ATTENDANCE_SUMMARIES.EMPLOYEE_NAME.asc())
                .fetch()
                .map(this::toMonthlySummary);
    }

    @Override
    public PageResult<MonthlySummary> findMonthlySummariesByDepartmentPaged(
            String departmentId, int year, int month,
            int page, int size, String sortField, String sortDirection) {

        // ソートフィールドをホワイトリストで検証する
        Field<?> sortColumn = MONTHLY_SORT_FIELDS.getOrDefault(sortField, MONTHLY_ATTENDANCE_SUMMARIES.EMPLOYEE_NAME);
        SortField<?> orderBy = "asc".equalsIgnoreCase(sortDirection) ? sortColumn.asc() : sortColumn.desc();

        Condition condition = MONTHLY_ATTENDANCE_SUMMARIES.DEPARTMENT_ID.eq(departmentId)
                .and(MONTHLY_ATTENDANCE_SUMMARIES.YEAR.eq((short) year))
                .and(MONTHLY_ATTENDANCE_SUMMARIES.MONTH.eq((short) month));

        // 全件数を取得する
        long totalElements = dsl.selectCount()
                .from(MONTHLY_ATTENDANCE_SUMMARIES)
                .where(condition)
                .fetchOne(0, long.class);

        // データを取得する（ページネーション + ソート付き）
        List<MonthlySummary> content = dsl.selectFrom(MONTHLY_ATTENDANCE_SUMMARIES)
                .where(condition)
                .orderBy(orderBy)
                .limit(size)
                .offset(page * size)
                .fetch()
                .map(this::toMonthlySummary);

        return PageResult.of(content, page, size, totalElements);
    }

    // ========================
    // 部門ダッシュボードクエリ
    // ========================

    @Override
    public List<DepartmentStats> findDepartmentStats(int year, int month) {
        // マテリアライズドビューから型安全に取得する
        return dsl.selectFrom(DEPARTMENT_ATTENDANCE_STATS)
                .where(DEPARTMENT_ATTENDANCE_STATS.YEAR.eq((short) year))
                .and(DEPARTMENT_ATTENDANCE_STATS.MONTH.eq((short) month))
                .orderBy(DEPARTMENT_ATTENDANCE_STATS.DEPARTMENT_NAME.asc())
                .fetch()
                .map(this::toDepartmentStats);
    }

    // ========================
    // 変換メソッド
    // ========================

    /** jOOQ Record → DailySummary DTO に変換 */
    private DailySummary toDailySummary(AttendanceSummariesRecord r) {
        return new DailySummary(
                r.getAttendanceId(),
                r.getEmployeeId(),
                r.getWorkDate(),
                r.getStatus(),
                r.getIsOnBreak(),
                r.getClockInTime() != null ? r.getClockInTime().toInstant() : null,
                r.getClockOutTime() != null ? r.getClockOutTime().toInstant() : null,
                r.getScheduledMinutes(),
                r.getActualMinutes(),
                r.getBreakMinutes(),
                r.getNetWorkMinutes(),
                r.getRegularOvertimeMinutes(),
                r.getLateNightMinutes(),
                r.getHolidayMinutes(),
                r.getTotalOvertimeMinutes(),
                r.getUpdatedAt().toInstant()
        );
    }

    /** jOOQ Record → MonthlySummary DTO に変換 */
    private MonthlySummary toMonthlySummary(MonthlyAttendanceSummariesRecord r) {
        return new MonthlySummary(
                r.getId(),
                r.getEmployeeId(),
                r.getEmployeeName(),
                r.getDepartmentId(),
                r.getYear().intValue(),
                r.getMonth().intValue(),
                r.getTotalWorkDays(),
                r.getTotalWorkMinutes(),
                r.getTotalOvertimeMinutes(),
                r.getTotalLateNightMinutes(),
                r.getTotalHolidayMinutes(),
                r.getTotalBreakMinutes(),
                r.getPaidLeaveUsed()
        );
    }

    /** jOOQ Record → DepartmentStats DTO に変換（マテビュー由来） */
    private DepartmentStats toDepartmentStats(DepartmentAttendanceStatsRecord r) {
        return new DepartmentStats(
                r.getDepartmentId(),
                r.getDepartmentName(),
                r.getYear().intValue(),
                r.getMonth().intValue(),
                r.getTotalEmployees().intValue(),
                r.getAvgWorkMinutes(),
                r.getAvgOvertimeMinutes(),
                r.getMaxOvertimeMinutes(),
                r.getTotalOvertimeMinutes().intValue(),
                r.getAvgLateNightMinutes(),
                r.getOvertimeAlertCount(),
                r.getMissingClockCount(),
                r.getAttendanceRate()
        );
    }
}
