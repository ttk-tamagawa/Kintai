package com.example.kintai.attendance.infrastructure.projector;

import com.example.kintai.attendance.domain.model.shift.event.ShiftPatternDeactivatedEvent;
import com.example.kintai.attendance.domain.model.shift.event.ShiftPatternDefinedEvent;
import com.example.kintai.attendance.domain.model.shift.event.ShiftPatternReactivatedEvent;
import com.example.kintai.attendance.infrastructure.persistence.entity.ShiftPatternSummaryJpaEntity;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * シフトパターンサマリープロジェクター — シフトパターンイベントを購読して shift_pattern_summaries を更新する
 *
 * <p>ShiftPattern 集約のドメインイベントを {@code @TransactionalEventListener} で購読し、
 * Read Model（shift_pattern_summaries）を UPSERT する。
 * Query 側（ShiftFinder）は本テーブルを参照することで Write Model（shift_patterns）から独立する。</p>
 *
 * <p>設計書: review-009 指摘 #2 — CQRS Read Model の一貫性確保</p>
 *
 * <p>対応イベント:
 * <ul>
 *   <li>ShiftPatternDefinedEvent → INSERT: 新規サマリー作成</li>
 *   <li>ShiftPatternDeactivatedEvent → UPDATE: is_active を false に更新</li>
 *   <li>ShiftPatternReactivatedEvent → UPDATE: is_active を true に更新</li>
 * </ul>
 * </p>
 *
 * <p>トランザクション方針: Write 側のコミット完了後（AFTER_COMMIT）に新規トランザクション
 * （REQUIRES_NEW）で実行する。Projector の失敗が Write 側ロールバックに波及しないようにする。</p>
 */
@Component
public class ShiftPatternSummaryProjector {

    private static final Logger log = LoggerFactory.getLogger(ShiftPatternSummaryProjector.class);
    private static final String SYSTEM_USER = "system";

    private final EntityManager entityManager;

    public ShiftPatternSummaryProjector(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // ========================================
    // パターン定義イベント → サマリー新規作成（INSERT）
    // ========================================

    /**
     * シフトパターン定義イベントを処理する
     *
     * <p>shift_pattern_summaries に新規行を作成し、パターンの基本情報と監査情報を設定する。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ShiftPatternDefinedEvent event) {
        log.debug("ShiftPatternDefinedEvent受信: patternId={}, name={}",
                event.patternId().value(), event.name().value());

        // 新規サマリーエンティティを作成する（is_active = true で初期化される）
        ShiftPatternSummaryJpaEntity entity = new ShiftPatternSummaryJpaEntity(
                event.patternId().value(),
                event.name().value(),
                event.startTime(),
                event.endTime(),
                event.breakMinutes(),
                event.isOvernight()
        );

        // イベント追跡情報を設定する
        entity.setLastEventAt(event.occurredAt());
        entity.setEventCount(1);

        entityManager.persist(entity);

        log.debug("シフトパターンサマリー作成完了: patternId={}", event.patternId().value());
    }

    // ========================================
    // パターン無効化イベント → is_active を false に更新
    // ========================================

    /**
     * シフトパターン無効化イベントを処理する
     *
     * <p>shift_pattern_summaries の is_active フラグを false に更新する。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ShiftPatternDeactivatedEvent event) {
        log.debug("ShiftPatternDeactivatedEvent受信: patternId={}", event.patternId().value());

        // 既存のサマリーを取得する（V12 初期投入 or Defined イベントで作成済み想定）
        ShiftPatternSummaryJpaEntity entity = findSummary(event.patternId().value());
        if (entity == null) {
            log.warn("シフトパターンサマリーが見つかりません: patternId={}", event.patternId().value());
            return;
        }

        // 有効フラグを false にして監査情報を更新する
        entity.setActive(false);
        updateEventTracking(entity, event.occurredAt());

        log.debug("シフトパターン無効化反映完了: patternId={}", event.patternId().value());
    }

    // ========================================
    // パターン再有効化イベント → is_active を true に更新
    // ========================================

    /**
     * シフトパターン再有効化イベントを処理する
     *
     * <p>shift_pattern_summaries の is_active フラグを true に更新する。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ShiftPatternReactivatedEvent event) {
        log.debug("ShiftPatternReactivatedEvent受信: patternId={}", event.patternId().value());

        // 既存のサマリーを取得する
        ShiftPatternSummaryJpaEntity entity = findSummary(event.patternId().value());
        if (entity == null) {
            log.warn("シフトパターンサマリーが見つかりません: patternId={}", event.patternId().value());
            return;
        }

        // 有効フラグを true にして監査情報を更新する
        entity.setActive(true);
        updateEventTracking(entity, event.occurredAt());

        log.debug("シフトパターン再有効化反映完了: patternId={}", event.patternId().value());
    }

    // ========================================
    // ヘルパー
    // ========================================

    /**
     * 主キーでサマリーを取得する（見つからなければ null）
     */
    private ShiftPatternSummaryJpaEntity findSummary(UUID patternId) {
        return entityManager.find(ShiftPatternSummaryJpaEntity.class, patternId);
    }

    /**
     * イベント追跡情報（lastEventAt / eventCount / updatedAt / updatedBy）を更新する
     *
     * <p>全イベントハンドラ共通の監査フィールド更新ロジック。</p>
     */
    private void updateEventTracking(ShiftPatternSummaryJpaEntity entity, Instant occurredAt) {
        entity.setLastEventAt(occurredAt);
        entity.setEventCount(entity.getEventCount() + 1);
        entity.setUpdatedAt(Instant.now());
        entity.setUpdatedBy(SYSTEM_USER);
    }
}
