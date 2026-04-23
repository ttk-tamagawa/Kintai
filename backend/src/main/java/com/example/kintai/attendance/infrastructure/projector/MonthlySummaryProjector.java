package com.example.kintai.attendance.infrastructure.projector;

import org.jooq.DSLContext;
import org.jooq.DatePart;
import org.jooq.Record2;
import org.jooq.Record6;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static com.example.kintai.attendance.infrastructure.jooq.generated.Tables.ATTENDANCE_SUMMARIES;
import static com.example.kintai.attendance.infrastructure.jooq.generated.Tables.EMPLOYEES;
import static com.example.kintai.attendance.infrastructure.jooq.generated.Tables.MONTHLY_ATTENDANCE_SUMMARIES;

/**
 * 月次勤怠サマリープロジェクター — 日次サマリー変更時に月次集計を更新する
 *
 * <p>AttendanceSummaryProjector から呼び出され、
 * attendance_summaries の集計結果を monthly_attendance_summaries に UPSERT する。
 * 更新後に DepartmentStatsRefresher を呼び出してマテリアライズドビューをリフレッシュする。</p>
 *
 * <p>設計書: 30_設計/データベース/勤怠記録.md の「monthly_attendance_summaries」に対応</p>
 */
@Component
public class MonthlySummaryProjector {

    private static final Logger log = LoggerFactory.getLogger(MonthlySummaryProjector.class);
    private static final String SYSTEM_USER = "system";
    /** プロジェクトの TZ 方針に従い、OffsetDateTime 生成は Asia/Tokyo で行う */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Tokyo");

    /** jOOQ DSLContext — 型安全な SQL アクセス */
    private final DSLContext dsl;

    /** マテリアライズドビューのリフレッシュに使用 */
    private final DepartmentStatsRefresher departmentStatsRefresher;

    public MonthlySummaryProjector(DSLContext dsl,
                                   DepartmentStatsRefresher departmentStatsRefresher) {
        this.dsl = dsl;
        this.departmentStatsRefresher = departmentStatsRefresher;
    }

    /**
     * 指定従業員の指定月の月次サマリーを再集計する
     *
     * <p>日次サマリー（attendance_summaries）から集計値を算出し、
     * 月次サマリー（monthly_attendance_summaries）を UPSERT する。
     * 従業員名・部門ID は employees テーブルから取得して非正規化する。</p>
     *
     * @param employeeId 従業員ID（文字列）
     * @param workDate   基準となる勤務日（年月の特定に使用）
     */
    public void recalculate(String employeeId, LocalDate workDate) {
        short year = (short) workDate.getYear();
        short month = (short) workDate.getMonthValue();

        log.debug("月次サマリー再集計: employeeId={}, year={}, month={}", employeeId, year, month);

        // ---- 1. 日次サマリーから月次集計値を算出する ----
        Aggregation agg = aggregateDailySummaries(employeeId, year, month);
        if (agg == null) {
            log.debug("集計対象の日次サマリーなし: employeeId={}", employeeId);
            return;
        }

        // ---- 2. 従業員名・部門IDをemployeesテーブルから取得する ----
        EmployeeInfo emp = lookupEmployeeInfo(employeeId);
        if (emp == null) {
            log.warn("従業員情報が見つかりません: employeeId={}", employeeId);
            return;
        }

        // ---- 3. 月次サマリーを UPSERT する（UNIQUE: employee_id + year + month）----
        OffsetDateTime now = OffsetDateTime.now(APP_ZONE);
        dsl.insertInto(MONTHLY_ATTENDANCE_SUMMARIES)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.ID, UUID.randomUUID())
                .set(MONTHLY_ATTENDANCE_SUMMARIES.EMPLOYEE_ID, employeeId)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.EMPLOYEE_NAME, emp.name)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.DEPARTMENT_ID, emp.departmentId)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.YEAR, year)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.MONTH, month)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_WORK_DAYS, agg.totalWorkDays)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_WORK_MINUTES, agg.totalWorkMinutes)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_OVERTIME_MINUTES, agg.totalOvertimeMinutes)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_LATE_NIGHT_MINUTES, agg.totalLateNightMinutes)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_HOLIDAY_MINUTES, agg.totalHolidayMinutes)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_BREAK_MINUTES, agg.totalBreakMinutes)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.PAID_LEAVE_USED, BigDecimal.ZERO)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.CREATED_AT, now)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.UPDATED_AT, now)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.CREATED_BY, SYSTEM_USER)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .onConflict(
                        MONTHLY_ATTENDANCE_SUMMARIES.EMPLOYEE_ID,
                        MONTHLY_ATTENDANCE_SUMMARIES.YEAR,
                        MONTHLY_ATTENDANCE_SUMMARIES.MONTH)
                .doUpdate()
                .set(MONTHLY_ATTENDANCE_SUMMARIES.EMPLOYEE_NAME, emp.name)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.DEPARTMENT_ID, emp.departmentId)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_WORK_DAYS, agg.totalWorkDays)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_WORK_MINUTES, agg.totalWorkMinutes)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_OVERTIME_MINUTES, agg.totalOvertimeMinutes)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_LATE_NIGHT_MINUTES, agg.totalLateNightMinutes)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_HOLIDAY_MINUTES, agg.totalHolidayMinutes)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.TOTAL_BREAK_MINUTES, agg.totalBreakMinutes)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.UPDATED_AT, now)
                .set(MONTHLY_ATTENDANCE_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .execute();

        log.debug("月次サマリー更新完了: employeeId={}, year={}, month={}, workDays={}",
                employeeId, year, month, agg.totalWorkDays);

        // ---- 4. 部門統計マテリアライズドビューをリフレッシュする ----
        departmentStatsRefresher.refresh();
    }

    // ========================================
    // 集計クエリ
    // ========================================

    /**
     * 日次サマリーから月次集計値を算出する
     *
     * <p>attendance_summaries から指定従業員・年月のレコードを集約し、
     * 出勤日数・勤務時間・残業時間等を計算する。
     * CLOCKED_OUT または FINALIZED のレコードを出勤日としてカウントする。</p>
     */
    private Aggregation aggregateDailySummaries(String employeeId, short year, short month) {
        Record6<Integer, Integer, Integer, Integer, Integer, Integer> row = dsl.select(
                        // CLOCKED_OUT/FINALIZED の日数を出勤日数としてカウント
                        DSL.coalesce(DSL.sum(DSL.when(
                                ATTENDANCE_SUMMARIES.STATUS.in("CLOCKED_OUT", "FINALIZED"),
                                DSL.inline(1)).otherwise(DSL.inline(0))), DSL.inline(0))
                                .cast(Integer.class),
                        DSL.coalesce(DSL.sum(ATTENDANCE_SUMMARIES.NET_WORK_MINUTES), DSL.inline(0)).cast(Integer.class),
                        DSL.coalesce(DSL.sum(ATTENDANCE_SUMMARIES.TOTAL_OVERTIME_MINUTES), DSL.inline(0)).cast(Integer.class),
                        DSL.coalesce(DSL.sum(ATTENDANCE_SUMMARIES.LATE_NIGHT_MINUTES), DSL.inline(0)).cast(Integer.class),
                        DSL.coalesce(DSL.sum(ATTENDANCE_SUMMARIES.HOLIDAY_MINUTES), DSL.inline(0)).cast(Integer.class),
                        DSL.coalesce(DSL.sum(ATTENDANCE_SUMMARIES.BREAK_MINUTES), DSL.inline(0)).cast(Integer.class))
                .from(ATTENDANCE_SUMMARIES)
                .where(ATTENDANCE_SUMMARIES.EMPLOYEE_ID.eq(employeeId))
                .and(DSL.extract(ATTENDANCE_SUMMARIES.WORK_DATE, DatePart.YEAR).eq((int) year))
                .and(DSL.extract(ATTENDANCE_SUMMARIES.WORK_DATE, DatePart.MONTH).eq((int) month))
                .and(ATTENDANCE_SUMMARIES.DELETED_AT.isNull())
                .fetchOne();

        if (row == null) {
            return null;
        }
        return new Aggregation(
                toInt(row.value1()),
                toInt(row.value2()),
                toInt(row.value3()),
                toInt(row.value4()),
                toInt(row.value5()),
                toInt(row.value6()));
    }

    /**
     * employees テーブルから従業員名と部門ID を取得する
     *
     * <p>月次サマリーに非正規化して保持するための従業員情報を取得する。</p>
     */
    private EmployeeInfo lookupEmployeeInfo(String employeeId) {
        Record2<String, String> row = dsl.select(EMPLOYEES.NAME, EMPLOYEES.DEPARTMENT_ID)
                .from(EMPLOYEES)
                .where(EMPLOYEES.EMPLOYEE_ID.eq(employeeId))
                .fetchOne();
        if (row == null) {
            return null;
        }
        return new EmployeeInfo(row.value1(), row.value2());
    }

    private int toInt(Integer value) {
        return value == null ? 0 : value;
    }

    /** 集計結果の内部 DTO */
    private record Aggregation(
            int totalWorkDays,
            int totalWorkMinutes,
            int totalOvertimeMinutes,
            int totalLateNightMinutes,
            int totalHolidayMinutes,
            int totalBreakMinutes) {}

    /** 従業員情報の内部 DTO */
    private record EmployeeInfo(String name, String departmentId) {}
}
