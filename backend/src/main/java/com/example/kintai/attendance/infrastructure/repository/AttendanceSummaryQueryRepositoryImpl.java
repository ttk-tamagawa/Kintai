package com.example.kintai.attendance.infrastructure.repository;

import com.example.kintai.attendance.domain.repository.AttendanceSummaryQueryRepository;
import com.example.kintai.attendance.infrastructure.persistence.entity.AttendanceSummaryJpaEntity;
import com.example.kintai.attendance.infrastructure.persistence.entity.MonthlySummaryJpaEntity;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 勤怠サマリークエリリポジトリ実装 — Read Model参照用
 *
 * <p>CQRS（コマンドクエリ責務分離）の読み取り側実装。
 * attendance_summaries、monthly_attendance_summaries、
 * department_attendance_statsのデータを取得する。
 * EntityManagerを使用してJPQLとネイティブクエリを実行する。</p>
 */
@Repository
@Transactional(readOnly = true)
public class AttendanceSummaryQueryRepositoryImpl implements AttendanceSummaryQueryRepository {

    @PersistenceContext
    private final EntityManager entityManager;

    public AttendanceSummaryQueryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // ========================
    // 日次サマリークエリ
    // ========================

    @Override
    public Optional<DailySummary> findDailySummaryByAttendanceId(AttendanceRecordId attendanceId) {
        // attendance_summariesテーブルからIDで検索
        AttendanceSummaryJpaEntity entity = entityManager.find(
                AttendanceSummaryJpaEntity.class, attendanceId.value()
        );
        return Optional.ofNullable(entity).map(this::toDailySummary);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DailySummary> findDailySummaries(EmployeeId employeeId, LocalDate from, LocalDate to) {
        // 従業員IDと日付範囲で日次サマリーを取得（日付降順）
        List<AttendanceSummaryJpaEntity> entities = entityManager.createQuery(
                "SELECT s FROM AttendanceSummaryJpaEntity s " +
                "WHERE s.employeeId = :employeeId " +
                "AND s.workDate BETWEEN :from AND :to " +
                "AND s.deletedAt IS NULL " +
                "ORDER BY s.workDate DESC"
        )
                .setParameter("employeeId", employeeId.value())
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        // JPAエンティティ → DailySummary DTOに変換
        List<DailySummary> result = new ArrayList<>();
        for (AttendanceSummaryJpaEntity e : entities) {
            result.add(toDailySummary(e));
        }
        return result;
    }

    // ========================
    // 月次サマリークエリ
    // ========================

    @Override
    public Optional<MonthlySummary> findMonthlySummary(EmployeeId employeeId, int year, int month) {
        // employee_id + year + monthのユニーク制約で1件取得
        @SuppressWarnings("unchecked")
        List<MonthlySummaryJpaEntity> results = entityManager.createQuery(
                "SELECT m FROM MonthlySummaryJpaEntity m " +
                "WHERE m.employeeId = :employeeId AND m.year = :year AND m.month = :month"
        )
                .setParameter("employeeId", employeeId.value())
                .setParameter("year", (short) year)
                .setParameter("month", (short) month)
                .getResultList();

        return results.stream().findFirst().map(this::toMonthlySummary);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MonthlySummary> findMonthlySummariesByDepartment(String departmentId, int year, int month) {
        // 部門の全従業員の月次サマリーを取得（従業員名順）
        List<MonthlySummaryJpaEntity> entities = entityManager.createQuery(
                "SELECT m FROM MonthlySummaryJpaEntity m " +
                "WHERE m.departmentId = :departmentId AND m.year = :year AND m.month = :month " +
                "ORDER BY m.employeeName ASC"
        )
                .setParameter("departmentId", departmentId)
                .setParameter("year", (short) year)
                .setParameter("month", (short) month)
                .getResultList();

        List<MonthlySummary> result = new ArrayList<>();
        for (MonthlySummaryJpaEntity e : entities) {
            result.add(toMonthlySummary(e));
        }
        return result;
    }

    // ========================
    // 部門ダッシュボードクエリ
    // ========================

    @Override
    @SuppressWarnings("unchecked")
    public List<DepartmentStats> findDepartmentStats(int year, int month) {
        // マテリアライズドビューからネイティブクエリで取得
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT department_id, department_name, year, month, " +
                "total_employees, avg_work_minutes, avg_overtime_minutes, " +
                "max_overtime_minutes, total_overtime_minutes, avg_late_night_minutes, " +
                "overtime_alert_count, missing_clock_count, attendance_rate " +
                "FROM department_attendance_stats " +
                "WHERE year = :year AND month = :month " +
                "ORDER BY department_name ASC"
        )
                .setParameter("year", year)
                .setParameter("month", month)
                .getResultList();

        // ネイティブクエリ結果 → DepartmentStats DTOに変換
        List<DepartmentStats> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new DepartmentStats(
                    (UUID) row[0],
                    (String) row[1],
                    ((Number) row[2]).intValue(),
                    ((Number) row[3]).intValue(),
                    ((Number) row[4]).intValue(),
                    (BigDecimal) row[5],
                    (BigDecimal) row[6],
                    ((Number) row[7]).intValue(),
                    ((Number) row[8]).intValue(),
                    (BigDecimal) row[9],
                    ((Number) row[10]).intValue(),
                    ((Number) row[11]).intValue(),
                    (BigDecimal) row[12]
            ));
        }
        return result;
    }

    // ========================
    // 変換メソッド
    // ========================

    /** JPAエンティティ → DailySummary DTOに変換 */
    private DailySummary toDailySummary(AttendanceSummaryJpaEntity e) {
        return new DailySummary(
                e.getAttendanceId(),
                e.getEmployeeId(),
                e.getWorkDate(),
                e.getStatus(),
                e.getClockInTime(),
                e.getClockOutTime(),
                e.getScheduledMinutes(),
                e.getActualMinutes(),
                e.getBreakMinutes(),
                e.getNetWorkMinutes(),
                e.getRegularOvertimeMinutes(),
                e.getLateNightMinutes(),
                e.getHolidayMinutes(),
                e.getTotalOvertimeMinutes()
        );
    }

    /** JPAエンティティ → MonthlySummary DTOに変換 */
    private MonthlySummary toMonthlySummary(MonthlySummaryJpaEntity e) {
        return new MonthlySummary(
                e.getId(),
                e.getEmployeeId(),
                e.getEmployeeName(),
                e.getDepartmentId(),
                e.getYear(),
                e.getMonth(),
                e.getTotalWorkDays(),
                e.getTotalWorkMinutes(),
                e.getTotalOvertimeMinutes(),
                e.getTotalLateNightMinutes(),
                e.getTotalHolidayMinutes(),
                e.getTotalBreakMinutes(),
                e.getPaidLeaveUsed()
        );
    }
}
