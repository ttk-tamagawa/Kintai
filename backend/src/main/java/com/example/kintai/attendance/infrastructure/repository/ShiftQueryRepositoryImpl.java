package com.example.kintai.attendance.infrastructure.repository;

import com.example.kintai.attendance.domain.repository.ShiftQueryRepository;
import com.example.kintai.attendance.infrastructure.persistence.entity.ShiftPatternJpaEntity;
import com.example.kintai.attendance.infrastructure.persistence.entity.WeeklyScheduleSummaryJpaEntity;
import com.example.kintai.shared.domain.model.EmployeeId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * シフトクエリリポジトリ実装 — Read Model 参照用
 *
 * <p>CQRS（コマンドクエリ責務分離）の読み取り側実装。
 * shift_patterns テーブルからパターン一覧を、
 * weekly_schedule_summaries テーブルからカレンダービュー用データを取得する。
 * EntityManager を使用して JPQL クエリを実行する。</p>
 */
@Repository
@Transactional(readOnly = true)
public class ShiftQueryRepositoryImpl implements ShiftQueryRepository {

    @PersistenceContext
    private final EntityManager entityManager;

    public ShiftQueryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // ========================
    // パターンクエリ
    // ========================

    @Override
    @SuppressWarnings("unchecked")
    public List<PatternSummary> findPatterns(Boolean isActive) {
        // isActive: true=有効のみ, false=無効のみ, null=全件（論理削除を除外）
        String jpql;
        if (isActive != null) {
            jpql = "SELECT p FROM ShiftPatternJpaEntity p " +
                    "WHERE p.isActive = :isActive AND p.deletedAt IS NULL " +
                    "ORDER BY p.name ASC";
        } else {
            jpql = "SELECT p FROM ShiftPatternJpaEntity p " +
                    "WHERE p.deletedAt IS NULL " +
                    "ORDER BY p.name ASC";
        }

        var query = entityManager.createQuery(jpql);
        if (isActive != null) {
            query.setParameter("isActive", isActive);
        }
        List<ShiftPatternJpaEntity> entities = query.getResultList();

        // JPAエンティティ → PatternSummary DTOに変換
        List<PatternSummary> result = new ArrayList<>();
        for (ShiftPatternJpaEntity e : entities) {
            result.add(toPatternSummary(e));
        }
        return result;
    }

    @Override
    public Optional<PatternSummary> findPatternById(UUID patternId) {
        // shift_patternsテーブルからIDで検索
        ShiftPatternJpaEntity entity = entityManager.find(ShiftPatternJpaEntity.class, patternId);
        return Optional.ofNullable(entity).map(this::toPatternSummary);
    }

    // ========================
    // カレンダービュークエリ
    // ========================

    @Override
    @SuppressWarnings("unchecked")
    public List<ScheduleSummary> findSchedules(EmployeeId employeeId, LocalDate from, LocalDate to) {
        // 従業員IDと期間で週次スケジュールサマリーを取得し、employeesテーブルからemployeeNameを結合する
        List<Object[]> rows = entityManager.createQuery(
                "SELECT s, e.name FROM WeeklyScheduleSummaryJpaEntity s " +
                "LEFT JOIN EmployeeJpaEntity e ON s.employeeId = e.employeeId " +
                "WHERE s.employeeId = :employeeId " +
                "AND s.weekStartDate BETWEEN :from AND :to " +
                "AND s.deletedAt IS NULL " +
                "ORDER BY s.weekStartDate DESC"
        )
                .setParameter("employeeId", employeeId.value())
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        // JPAエンティティ + employeeName → ScheduleSummary DTOに変換
        List<ScheduleSummary> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(toScheduleSummary((WeeklyScheduleSummaryJpaEntity) row[0], (String) row[1]));
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ScheduleSummary> findAllSchedules(LocalDate from, LocalDate to) {
        // 全従業員の週次スケジュールサマリーを期間で取得し、employeesテーブルからemployeeNameを結合する
        List<Object[]> rows = entityManager.createQuery(
                "SELECT s, e.name FROM WeeklyScheduleSummaryJpaEntity s " +
                "LEFT JOIN EmployeeJpaEntity e ON s.employeeId = e.employeeId " +
                "WHERE s.weekStartDate BETWEEN :from AND :to " +
                "AND s.deletedAt IS NULL " +
                "ORDER BY s.weekStartDate ASC, s.employeeId ASC"
        )
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        // JPAエンティティ + employeeName → ScheduleSummary DTOに変換
        List<ScheduleSummary> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(toScheduleSummary((WeeklyScheduleSummaryJpaEntity) row[0], (String) row[1]));
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<ScheduleSummary> findScheduleById(UUID scheduleId) {
        // weekly_schedule_summariesテーブルからIDで検索し、employeesテーブルからemployeeNameを結合する
        List<Object[]> rows = entityManager.createQuery(
                "SELECT s, e.name FROM WeeklyScheduleSummaryJpaEntity s " +
                "LEFT JOIN EmployeeJpaEntity e ON s.employeeId = e.employeeId " +
                "WHERE s.weeklyScheduleId = :scheduleId AND s.deletedAt IS NULL"
        )
                .setParameter("scheduleId", scheduleId)
                .getResultList();

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.getFirst();
        return Optional.of(toScheduleSummary((WeeklyScheduleSummaryJpaEntity) row[0], (String) row[1]));
    }

    // ========================
    // 変換メソッド
    // ========================

    /** JPAエンティティ → PatternSummary DTOに変換 */
    private PatternSummary toPatternSummary(ShiftPatternJpaEntity e) {
        return new PatternSummary(
                e.getId(),
                e.getName(),
                e.getStartTime().toString(),
                e.getEndTime().toString(),
                e.getBreakMinutes(),
                e.isOvernight(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    /** JPAエンティティ + employeeName → ScheduleSummary DTOに変換 */
    private ScheduleSummary toScheduleSummary(WeeklyScheduleSummaryJpaEntity e, String employeeName) {
        return new ScheduleSummary(
                e.getWeeklyScheduleId(),
                e.getEmployeeId(),
                employeeName != null ? employeeName : "",
                e.getWeekStartDate(),
                e.getStatus(),
                e.getMondayPatternId(), e.getMondayPatternName(),
                e.getTuesdayPatternId(), e.getTuesdayPatternName(),
                e.getWednesdayPatternId(), e.getWednesdayPatternName(),
                e.getThursdayPatternId(), e.getThursdayPatternName(),
                e.getFridayPatternId(), e.getFridayPatternName(),
                e.getSaturdayPatternId(), e.getSaturdayPatternName(),
                e.getSundayPatternId(), e.getSundayPatternName(),
                e.getAssignedDays(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
