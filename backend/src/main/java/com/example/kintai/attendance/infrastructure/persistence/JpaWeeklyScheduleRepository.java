package com.example.kintai.attendance.infrastructure.persistence;

import com.example.kintai.attendance.infrastructure.persistence.entity.WeeklyScheduleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
