package com.example.kintai.attendance.infrastructure.projector;

import com.example.kintai.attendance.infrastructure.persistence.entity.MonthlySummaryJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 月次勤怠サマリープロジェクター — 日次サマリー変更時に月次集計を更新する
 *
 * <p>AttendanceSummaryProjectorから呼び出され、
 * attendance_summariesの集計結果をmonthly_attendance_summariesにUPSERTする。
 * 更新後にDepartmentStatsRefresherを呼び出してマテリアライズドビューをリフレッシュする。</p>
 *
 * <p>設計書: 30_設計/データベース/勤怠記録.md の「monthly_attendance_summaries」に対応</p>
 */
@Component
public class MonthlySummaryProjector {

    private static final Logger log = LoggerFactory.getLogger(MonthlySummaryProjector.class);
    private static final String SYSTEM_USER = "system";

    private final EntityManager entityManager;
    private final DepartmentStatsRefresher departmentStatsRefresher;

    public MonthlySummaryProjector(EntityManager entityManager,
                                   DepartmentStatsRefresher departmentStatsRefresher) {
        this.entityManager = entityManager;
        this.departmentStatsRefresher = departmentStatsRefresher;
    }

    /**
     * 指定従業員の指定月の月次サマリーを再集計する
     *
     * <p>日次サマリー（attendance_summaries）から集計値を算出し、
     * 月次サマリー（monthly_attendance_summaries）をUPSERTする。
     * 従業員名・部門IDはemployeesテーブルから取得して非正規化する。</p>
     *
     * @param employeeId 従業員ID（文字列）
     * @param workDate   基準となる勤務日（年月の特定に使用）
     */
    public void recalculate(String employeeId, LocalDate workDate) {
        short year = (short) workDate.getYear();
        short month = (short) workDate.getMonthValue();

        log.debug("月次サマリー再集計: employeeId={}, year={}, month={}", employeeId, year, month);

        // ---- 1. 日次サマリーから月次集計値を算出する ----
        Object[] aggregation = aggregateDailySummaries(employeeId, year, month);
        if (aggregation == null) {
            log.debug("集計対象の日次サマリーなし: employeeId={}", employeeId);
            return;
        }

        // 集計結果を取り出す（null安全にゼロ変換）
        int totalWorkDays = toInt(aggregation[0]);
        int totalWorkMinutes = toInt(aggregation[1]);
        int totalOvertimeMinutes = toInt(aggregation[2]);
        int totalLateNightMinutes = toInt(aggregation[3]);
        int totalHolidayMinutes = toInt(aggregation[4]);
        int totalBreakMinutes = toInt(aggregation[5]);

        // ---- 2. 従業員名・部門IDをemployeesテーブルから取得する ----
        Object[] employeeInfo = lookupEmployeeInfo(employeeId);
        if (employeeInfo == null) {
            log.warn("従業員情報が見つかりません: employeeId={}", employeeId);
            return;
        }
        String employeeName = (String) employeeInfo[0];
        String departmentId = (String) employeeInfo[1];

        // ---- 3. 月次サマリーをUPSERTする ----
        MonthlySummaryJpaEntity entity = findOrCreateMonthlySummary(
                employeeId, employeeName, departmentId, year, month);

        // 集計値を設定する
        entity.setTotalWorkDays(totalWorkDays);
        entity.setTotalWorkMinutes(totalWorkMinutes);
        entity.setTotalOvertimeMinutes(totalOvertimeMinutes);
        entity.setTotalLateNightMinutes(totalLateNightMinutes);
        entity.setTotalHolidayMinutes(totalHolidayMinutes);
        entity.setTotalBreakMinutes(totalBreakMinutes);
        entity.setUpdatedAt(Instant.now());
        entity.setUpdatedBy(SYSTEM_USER);

        entityManager.merge(entity);

        log.debug("月次サマリー更新完了: employeeId={}, year={}, month={}, workDays={}",
                employeeId, year, month, totalWorkDays);

        // ---- 4. 部門統計マテリアライズドビューをリフレッシュする ----
        departmentStatsRefresher.refresh();
    }

    // ========================================
    // 集計クエリ
    // ========================================

    /**
     * 日次サマリーから月次集計値を算出する
     *
     * <p>attendance_summariesから指定従業員・年月のレコードを集約し、
     * 出勤日数・勤務時間・残業時間等を計算する。
     * CLOCKED_OUTまたはFINALIZEDのレコードを出勤日としてカウントする。</p>
     */
    private Object[] aggregateDailySummaries(String employeeId, short year, short month) {
        try {
            return (Object[]) entityManager.createQuery(
                    // CLOCKED_OUT/FINALIZEDの日数を出勤日数としてカウントする
                    "SELECT " +
                    "  SUM(CASE WHEN s.status IN ('CLOCKED_OUT','FINALIZED') THEN 1 ELSE 0 END), " +
                    "  COALESCE(SUM(s.netWorkMinutes), 0), " +
                    "  COALESCE(SUM(s.totalOvertimeMinutes), 0), " +
                    "  COALESCE(SUM(s.lateNightMinutes), 0), " +
                    "  COALESCE(SUM(s.holidayMinutes), 0), " +
                    "  COALESCE(SUM(s.breakMinutes), 0) " +
                    "FROM AttendanceSummaryJpaEntity s " +
                    "WHERE s.employeeId = :employeeId " +
                    "  AND EXTRACT(YEAR FROM s.workDate) = :year " +
                    "  AND EXTRACT(MONTH FROM s.workDate) = :month " +
                    "  AND s.deletedAt IS NULL"
            )
            .setParameter("employeeId", employeeId)
            .setParameter("year", (int) year)
            .setParameter("month", (int) month)
            .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * employeesテーブルから従業員名と部門IDを取得する
     *
     * <p>月次サマリーに非正規化して保持するための従業員情報を取得する。
     * employeesテーブルのemployee_idはVARCHAR(36)のため文字列で検索する。</p>
     */
    private Object[] lookupEmployeeInfo(String employeeId) {
        try {
            return (Object[]) entityManager.createNativeQuery(
                    // 従業員名と部門IDを取得する
                    "SELECT name, department_id FROM employees WHERE employee_id = :id"
            )
            .setParameter("id", employeeId)
            .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * 月次サマリーを取得する。存在しない場合は新規作成する
     *
     * <p>employee_id + year + month のUNIQUE制約に基づいて検索する。
     * 初回はUUIDを自動生成して新規エンティティを作成する。</p>
     */
    private MonthlySummaryJpaEntity findOrCreateMonthlySummary(
            String employeeId, String employeeName, String departmentId,
            short year, short month) {

        // employee_id + year + month で既存レコードを検索する
        return entityManager.createQuery(
                "SELECT m FROM MonthlySummaryJpaEntity m " +
                "WHERE m.employeeId = :employeeId AND m.year = :year AND m.month = :month",
                MonthlySummaryJpaEntity.class
        )
        .setParameter("employeeId", employeeId)
        .setParameter("year", year)
        .setParameter("month", month)
        .getResultStream()
        .findFirst()
        .orElseGet(() -> {
            // 初回: 新規月次サマリーを作成する
            log.debug("新規月次サマリー作成: employeeId={}, year={}, month={}", employeeId, year, month);
            return new MonthlySummaryJpaEntity(
                    UUID.randomUUID(), employeeId, employeeName,
                    departmentId, year, month);
        });
    }

    /**
     * Object値をint型に安全に変換する（null → 0）
     */
    private int toInt(Object value) {
        if (value == null) return 0;
        return ((Number) value).intValue();
    }
}
