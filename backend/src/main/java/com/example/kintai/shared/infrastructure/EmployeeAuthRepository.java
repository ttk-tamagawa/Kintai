package com.example.kintai.shared.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 従業員認証用リポジトリ — メールアドレスで従業員を検索する
 */
public interface EmployeeAuthRepository extends JpaRepository<EmployeeJpaEntity, String> {

    /** メールアドレスで有効な従業員を検索する */
    Optional<EmployeeJpaEntity> findByEmailAndIsActiveTrue(String email);
}
