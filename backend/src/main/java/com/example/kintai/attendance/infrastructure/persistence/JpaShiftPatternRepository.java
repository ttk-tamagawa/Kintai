package com.example.kintai.attendance.infrastructure.persistence;

import com.example.kintai.attendance.infrastructure.persistence.entity.ShiftPatternJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * シフトパターンSpring Data JPAリポジトリ — CRUDの自動生成
 *
 * <p>Spring Data JPAがインターフェースのメソッド名を解析し、
 * 実装クラスを自動生成する。
 * ShiftPatternRepositoryImplから内部的に使用される。</p>
 */
public interface JpaShiftPatternRepository extends JpaRepository<ShiftPatternJpaEntity, UUID> {

    /**
     * パターン名でシフトパターンが存在するか確認する
     *
     * <p>INV-SH-001（パターン名一意性）の検証に使用する。</p>
     */
    boolean existsByName(String name);

    /**
     * 有効なパターンのみを取得する（論理削除を除外）
     */
    List<ShiftPatternJpaEntity> findByIsActiveTrueAndDeletedAtIsNull();

    /**
     * 全パターンを取得する（論理削除を除外）
     */
    List<ShiftPatternJpaEntity> findByDeletedAtIsNull();
}
