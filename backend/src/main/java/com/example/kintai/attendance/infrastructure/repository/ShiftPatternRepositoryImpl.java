package com.example.kintai.attendance.infrastructure.repository;

import com.example.kintai.attendance.domain.model.shift.PatternName;
import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.infrastructure.persistence.JpaShiftPatternRepository;
import com.example.kintai.attendance.infrastructure.persistence.entity.ShiftPatternJpaEntity;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * シフトパターンリポジトリ実装 — JPA を使用した DB 永続化
 *
 * <p>ドメイン層の ShiftPatternRepository インターフェースを実装する。
 * ShiftPattern ドメインモデルと ShiftPatternJpaEntity の相互変換を行い、
 * Spring Data JPA 経由で shift_patterns テーブルに永続化する。
 * 楽観的ロックは JPA の @Version アノテーションで自動管理される。</p>
 */
@Repository
@Transactional
public class ShiftPatternRepositoryImpl implements ShiftPatternRepository {

    private final JpaShiftPatternRepository jpaShiftPatternRepo;

    /** 認証未実装のため、監査カラムにはシステムユーザーを設定 */
    private static final String SYSTEM_USER = "system";

    public ShiftPatternRepositoryImpl(JpaShiftPatternRepository jpaShiftPatternRepo) {
        this.jpaShiftPatternRepo = jpaShiftPatternRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShiftPattern> findById(ShiftPatternId id) {
        // shift_patternsテーブルからIDで検索し、ドメインモデルに変換
        return jpaShiftPatternRepo.findById(id.value())
                .map(this::toDomainModel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftPattern> findAllById(Collection<ShiftPatternId> ids) {
        // 空集合の場合はDBアクセスせず空リストを返す
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        // ShiftPatternId → UUID に変換してJPAに渡す（IN句による1クエリ取得）
        List<UUID> uuidIds = ids.stream()
                .map(ShiftPatternId::value)
                .collect(Collectors.toList());

        // JpaRepositoryのfindAllByIdでまとめて取得（SELECT ... WHERE id IN (...) 1本）
        List<ShiftPatternJpaEntity> entities = jpaShiftPatternRepo.findAllById(uuidIds);

        // JPAエンティティ → ドメインモデルに変換
        List<ShiftPattern> result = new ArrayList<>(entities.size());
        for (ShiftPatternJpaEntity entity : entities) {
            result.add(toDomainModel(entity));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftPattern> findAll(boolean activeOnly) {
        // activeOnlyフラグに応じて有効パターンのみ or 全パターンを取得
        List<ShiftPatternJpaEntity> entities;
        if (activeOnly) {
            entities = jpaShiftPatternRepo.findByIsActiveTrueAndDeletedAtIsNull();
        } else {
            entities = jpaShiftPatternRepo.findByDeletedAtIsNull();
        }

        // JPAエンティティ → ドメインモデルに変換
        List<ShiftPattern> result = new ArrayList<>();
        for (ShiftPatternJpaEntity entity : entities) {
            result.add(toDomainModel(entity));
        }
        return result;
    }

    @Override
    public ShiftPattern save(ShiftPattern pattern) {
        // ドメインモデル → JPAエンティティに変換
        ShiftPatternJpaEntity entity = toJpaEntity(pattern);

        // shift_patternsテーブルに保存（JPAがpersist/mergeを自動判定）
        ShiftPatternJpaEntity saved = jpaShiftPatternRepo.saveAndFlush(entity);

        // 保存後のバージョンを反映したドメインオブジェクトを返す
        return ShiftPattern.reconstruct(
                ShiftPatternId.of(saved.getId()),
                new PatternName(saved.getName()),
                saved.getStartTime(),
                saved.getEndTime(),
                saved.getBreakMinutes(),
                saved.isOvernight(),
                saved.isActive(),
                saved.getVersion(),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(PatternName name) {
        // パターン名の一意性チェック（INV-SH-001）
        return jpaShiftPatternRepo.existsByName(name.value());
    }

    // ========================
    // 変換メソッド
    // ========================

    /**
     * JPAエンティティ → ドメインモデルに変換する
     */
    private ShiftPattern toDomainModel(ShiftPatternJpaEntity entity) {
        return ShiftPattern.reconstruct(
                ShiftPatternId.of(entity.getId()),
                new PatternName(entity.getName()),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getBreakMinutes(),
                entity.isOvernight(),
                entity.isActive(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * ドメインモデル → JPAエンティティに変換する
     */
    private ShiftPatternJpaEntity toJpaEntity(ShiftPattern pattern) {
        return new ShiftPatternJpaEntity(
                pattern.getId().value(),
                pattern.getName().value(),
                pattern.getStartTime(),
                pattern.getEndTime(),
                pattern.getBreakMinutes(),
                pattern.isOvernight(),
                pattern.isActive(),
                pattern.getVersion(),
                pattern.getCreatedAt(),
                pattern.getUpdatedAt(),
                SYSTEM_USER,
                SYSTEM_USER
        );
    }
}
