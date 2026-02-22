package com.example.kintai.attendance.infrastructure.persistence;

import com.example.kintai.attendance.infrastructure.persistence.entity.WeeklyScheduleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * 週次スケジュールSpring Data JPAリポジトリ — CRUDの自動生成
 *
 * <p>Spring Data JPAがインターフェースのメソッド名を解析し、
 * 実装クラスを自動生成する。
 * WeeklyScheduleRepositoryImplから内部的に使用される。</p>
 */
public interface JpaWeeklyScheduleRepository extends JpaRepository<WeeklyScheduleJpaEntity, UUID> {

    /**
     * 従業員IDと週開始日で検索する
     *
     * <p>1従業員1週1スケジュールのユニーク制約を利用した検索。</p>
     */
    Optional<WeeklyScheduleJpaEntity> findByEmployeeIdAndWeekStartDate(UUID employeeId, LocalDate weekStartDate);

    /**
     * 従業員IDと週開始日の組み合わせが既に存在するか確認する
     */
    boolean existsByEmployeeIdAndWeekStartDate(UUID employeeId, LocalDate weekStartDate);

    /**
     * 未来の週次スケジュールで指定パターンIDが使用されているか確認する
     *
     * <p>月〜日の7つのパターンカラムのいずれかに一致し、
     * かつ週開始日が指定日以降のスケジュールが存在するかチェックする。</p>
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
            "FROM WeeklyScheduleJpaEntity s " +
            "WHERE s.weekStartDate >= :today AND s.deletedAt IS NULL AND " +
            "(s.mondayPatternId = :patternId OR s.tuesdayPatternId = :patternId OR " +
            "s.wednesdayPatternId = :patternId OR s.thursdayPatternId = :patternId OR " +
            "s.fridayPatternId = :patternId OR s.saturdayPatternId = :patternId OR " +
            "s.sundayPatternId = :patternId)")
    boolean existsFutureAssignmentByPatternId(
            @Param("patternId") UUID patternId,
            @Param("today") LocalDate today);
}
