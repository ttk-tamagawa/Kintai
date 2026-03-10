package com.example.kintai.shared.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 従業員ロールリポジトリ — 従業員IDでロール一覧を検索する
 */
public interface EmployeeRoleRepository extends JpaRepository<EmployeeRoleJpaEntity, Long> {

    /** 従業員IDに紐づくロール一覧を取得する */
    List<EmployeeRoleJpaEntity> findByEmployeeId(String employeeId);
}
