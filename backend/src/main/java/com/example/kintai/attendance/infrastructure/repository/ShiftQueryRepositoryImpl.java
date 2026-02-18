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
    public List<PatternSummary> findPatterns(boolean activeOnly) {
        // 有効パターンのみ or 全パターンを取得（論理削除を除外）
        String jpql;
        if (activeOnly) {
            jpql = "SELECT p FROM ShiftPatternJpaEntity p " +
                    "WHERE p.isActive = true AND p.deletedAt IS NULL " +
                    "ORDER BY p.name ASC";
        } else {
            jpql = "SELECT p FROM ShiftPatternJpaEntity p " +
                    "WHERE p.deletedAt IS NULL " +
                    "ORDER BY p.name ASC";
        }

        List<ShiftPatternJpaEntity> entities = entityManager.createQuery(jpql).getResultList();

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
        // 従業員IDと期間で週次スケジュールサマリーを取得（週開始日降順）
        List<WeeklyScheduleSummaryJpaEntity> entities = entityManager.createQuery(
                "SELECT s FROM WeeklyScheduleSummaryJpaEntity s " +
                "WHERE s.employeeId = :employeeId " +
                "AND s.weekStartDate BETWEEN :from AND :to " +
                "AND s.deletedAt IS NULL " +
                "ORDER BY s.weekStartDate DESC"
        )
                .setParameter("employeeId", employeeId.value())
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        // JPAエンティティ → ScheduleSummary DTOに変換
        List<ScheduleSummary> result = new ArrayList<>();
        for (WeeklyScheduleSummaryJpaEntity e : entities) {
            result.add(toScheduleSummary(e));
        }
        return result;
    }

    @Override
    public Optional<ScheduleSummary> findScheduleById(UUID scheduleId) {
        // weekly_schedule_summariesテーブルからIDで検索
        WeeklyScheduleSummaryJpaEntity entity = entityManager.find(
                WeeklyScheduleSummaryJpaEntity.class, scheduleId
        );
        return Optional.ofNullable(entity).map(this::toScheduleSummary);
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
                e.isActive()
        );
    }

    /** JPAエンティティ → ScheduleSummary DTOに変換 */
    private ScheduleSummary toScheduleSummary(WeeklyScheduleSummaryJpaEntity e) {
        return new ScheduleSummary(
                e.getWeeklyScheduleId(),
                e.getEmployeeId(),
                e.getWeekStartDate(),
                e.getStatus(),
                e.getMondayPatternId(), e.getMondayPatternName(),
                e.getTuesdayPatternId(), e.getTuesdayPatternName(),
                e.getWednesdayPatternId(), e.getWednesdayPatternName(),
                e.getThursdayPatternId(), e.getThursdayPatternName(),
                e.getFridayPatternId(), e.getFridayPatternName(),
                e.getSaturdayPatternId(), e.getSaturdayPatternName(),
                e.getSundayPatternId(), e.getSundayPatternName(),
                e.getAssignedDays()
        );
    }
}
