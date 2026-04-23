package com.example.kintai.attendance.infrastructure.repository;

import com.example.kintai.attendance.application.query.ShiftFinder;
import com.example.kintai.attendance.infrastructure.jooq.generated.tables.records.ShiftPatternSummariesRecord;
import com.example.kintai.shared.domain.model.EmployeeId;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.kintai.attendance.infrastructure.jooq.generated.Tables.EMPLOYEES;
import static com.example.kintai.attendance.infrastructure.jooq.generated.Tables.SHIFT_PATTERN_SUMMARIES;
import static com.example.kintai.attendance.infrastructure.jooq.generated.Tables.WEEKLY_SCHEDULE_SUMMARIES;

/**
 * シフトファインダー実装 — Read Model 参照用
 *
 * <p>CQRS（コマンドクエリ責務分離）の読み取り側実装。
 * shift_pattern_summaries テーブルからパターン一覧を、
 * weekly_schedule_summaries テーブルからカレンダービュー用データを取得する。
 * Write Model（shift_patterns）には一切アクセスしない。
 * jOOQ DSL で型安全に SQL を組み立てる。</p>
 */
@Repository
@Transactional(readOnly = true)
public class ShiftFinderImpl implements ShiftFinder {

    /** カラム別名: employees テーブルの name を employee_name として取得するための識別子 */
    private static final String EMPLOYEE_NAME_ALIAS = "employee_name";

    /** jOOQ DSLContext — 型安全な SQL アクセス */
    private final DSLContext dsl;

    public ShiftFinderImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ========================
    // パターンクエリ
    // ========================

    @Override
    public List<PatternSummary> findPatterns(Boolean isActive) {
        // 論理削除されていないレコードだけを対象に、isActive フィルタを条件合成する
        // isActive: true=有効のみ, false=無効のみ, null=全件
        Condition condition = SHIFT_PATTERN_SUMMARIES.DELETED_AT.isNull();
        if (isActive != null) {
            condition = condition.and(SHIFT_PATTERN_SUMMARIES.IS_ACTIVE.eq(isActive));
        }

        // 型安全な jOOQ DSL でクエリを組み立てる（カラム名 typo はコンパイル時に検出される）
        return dsl.selectFrom(SHIFT_PATTERN_SUMMARIES)
                .where(condition)
                .orderBy(SHIFT_PATTERN_SUMMARIES.NAME.asc())
                .fetch()
                .map(this::toPatternSummary);
    }

    @Override
    public Optional<PatternSummary> findPatternById(UUID patternId) {
        // shift_pattern_summaries テーブルから主キーで検索する
        return dsl.selectFrom(SHIFT_PATTERN_SUMMARIES)
                .where(SHIFT_PATTERN_SUMMARIES.SHIFT_PATTERN_ID.eq(patternId))
                .fetchOptional()
                .map(this::toPatternSummary);
    }

    // ========================
    // カレンダービュークエリ
    // ========================

    @Override
    public List<ScheduleSummary> findSchedules(EmployeeId employeeId, LocalDate from, LocalDate to) {
        // 従業員IDと期間で週次スケジュールサマリーを取得し、employeesテーブルからemployeeNameを結合する
        return dsl.select(WEEKLY_SCHEDULE_SUMMARIES.asterisk(), EMPLOYEES.NAME.as(EMPLOYEE_NAME_ALIAS))
                .from(WEEKLY_SCHEDULE_SUMMARIES)
                .leftJoin(EMPLOYEES)
                    .on(WEEKLY_SCHEDULE_SUMMARIES.EMPLOYEE_ID.eq(EMPLOYEES.EMPLOYEE_ID))
                .where(WEEKLY_SCHEDULE_SUMMARIES.EMPLOYEE_ID.eq(employeeId.value()))
                .and(WEEKLY_SCHEDULE_SUMMARIES.WEEK_START_DATE.between(from, to))
                .and(WEEKLY_SCHEDULE_SUMMARIES.DELETED_AT.isNull())
                .orderBy(WEEKLY_SCHEDULE_SUMMARIES.WEEK_START_DATE.desc())
                .fetch(this::toScheduleSummary);
    }

    @Override
    public List<ScheduleSummary> findAllSchedules(LocalDate from, LocalDate to) {
        // 全従業員の週次スケジュールサマリーを期間で取得し、employeesテーブルからemployeeNameを結合する
        return dsl.select(WEEKLY_SCHEDULE_SUMMARIES.asterisk(), EMPLOYEES.NAME.as(EMPLOYEE_NAME_ALIAS))
                .from(WEEKLY_SCHEDULE_SUMMARIES)
                .leftJoin(EMPLOYEES)
                    .on(WEEKLY_SCHEDULE_SUMMARIES.EMPLOYEE_ID.eq(EMPLOYEES.EMPLOYEE_ID))
                .where(WEEKLY_SCHEDULE_SUMMARIES.WEEK_START_DATE.between(from, to))
                .and(WEEKLY_SCHEDULE_SUMMARIES.DELETED_AT.isNull())
                .orderBy(WEEKLY_SCHEDULE_SUMMARIES.WEEK_START_DATE.asc(), WEEKLY_SCHEDULE_SUMMARIES.EMPLOYEE_ID.asc())
                .fetch(this::toScheduleSummary);
    }

    @Override
    public Optional<ScheduleSummary> findScheduleById(UUID scheduleId) {
        // weekly_schedule_summariesテーブルからIDで検索し、employeesテーブルからemployeeNameを結合する
        return dsl.select(WEEKLY_SCHEDULE_SUMMARIES.asterisk(), EMPLOYEES.NAME.as(EMPLOYEE_NAME_ALIAS))
                .from(WEEKLY_SCHEDULE_SUMMARIES)
                .leftJoin(EMPLOYEES)
                    .on(WEEKLY_SCHEDULE_SUMMARIES.EMPLOYEE_ID.eq(EMPLOYEES.EMPLOYEE_ID))
                .where(WEEKLY_SCHEDULE_SUMMARIES.WEEKLY_SCHEDULE_ID.eq(scheduleId))
                .and(WEEKLY_SCHEDULE_SUMMARIES.DELETED_AT.isNull())
                .fetchOptional(this::toScheduleSummary);
    }

    // ========================
    // 変換メソッド
    // ========================

    /** jOOQ Record → PatternSummary DTO に変換 */
    private PatternSummary toPatternSummary(ShiftPatternSummariesRecord r) {
        return new PatternSummary(
                r.getShiftPatternId(),
                r.getName(),
                r.getStartTime().toString(),
                r.getEndTime().toString(),
                r.getBreakMinutes(),
                r.getIsOvernight(),
                r.getIsActive(),
                r.getCreatedAt().toInstant(),
                r.getUpdatedAt().toInstant()
        );
    }

    /** jOOQ Record (WSS + employees JOIN) → ScheduleSummary DTO に変換 */
    private ScheduleSummary toScheduleSummary(Record r) {
        // employees が見つからなかった場合は空文字をデフォルトにする（LEFT JOIN のため）
        String employeeName = r.get(EMPLOYEE_NAME_ALIAS, String.class);
        return new ScheduleSummary(
                r.get(WEEKLY_SCHEDULE_SUMMARIES.WEEKLY_SCHEDULE_ID),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.EMPLOYEE_ID),
                employeeName != null ? employeeName : "",
                r.get(WEEKLY_SCHEDULE_SUMMARIES.WEEK_START_DATE),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.STATUS),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.MONDAY_PATTERN_ID),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.MONDAY_PATTERN_NAME),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.TUESDAY_PATTERN_ID),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.TUESDAY_PATTERN_NAME),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.WEDNESDAY_PATTERN_ID),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.WEDNESDAY_PATTERN_NAME),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.THURSDAY_PATTERN_ID),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.THURSDAY_PATTERN_NAME),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.FRIDAY_PATTERN_ID),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.FRIDAY_PATTERN_NAME),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.SATURDAY_PATTERN_ID),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.SATURDAY_PATTERN_NAME),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.SUNDAY_PATTERN_ID),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.SUNDAY_PATTERN_NAME),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.ASSIGNED_DAYS),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.CREATED_AT).toInstant(),
                r.get(WEEKLY_SCHEDULE_SUMMARIES.UPDATED_AT).toInstant()
        );
    }
}
