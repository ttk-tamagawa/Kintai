package com.example.kintai.attendance.infrastructure.persistence;

import com.example.kintai.attendance.infrastructure.persistence.entity.AttendanceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * 勤怠記録 Spring Data JPAリポジトリ — attendancesテーブルのDB操作
 *
 * <p>Spring Data JPAが自動実装するインフラ層のDB操作インターフェース。
 * ドメインリポジトリ（AttendanceRecordRepository）の実装で内部的に使用する。</p>
 */
public interface JpaAttendanceRepository extends JpaRepository<AttendanceJpaEntity, UUID> {

    /**
     * 従業員IDと勤務日で検索する
     */
    Optional<AttendanceJpaEntity> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);

    /**
     * 従業員IDと勤務日の組み合わせが存在するか確認する
     */
    boolean existsByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);
}
