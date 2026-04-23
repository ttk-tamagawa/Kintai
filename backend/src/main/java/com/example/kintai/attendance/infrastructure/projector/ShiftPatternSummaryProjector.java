package com.example.kintai.attendance.infrastructure.projector;

import com.example.kintai.attendance.domain.model.shift.event.ShiftPatternDeactivatedEvent;
import com.example.kintai.attendance.domain.model.shift.event.ShiftPatternDefinedEvent;
import com.example.kintai.attendance.domain.model.shift.event.ShiftPatternReactivatedEvent;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static com.example.kintai.attendance.infrastructure.jooq.generated.Tables.SHIFT_PATTERN_SUMMARIES;

/**
 * シフトパターンサマリープロジェクター — シフトパターンイベントを購読して shift_pattern_summaries を更新する
 *
 * <p>ShiftPattern 集約のドメインイベントを {@code @TransactionalEventListener} で購読し、
 * Read Model（shift_pattern_summaries）を jOOQ 経由で更新する。
 * Query 側（ShiftFinder）は本テーブルを参照することで Write Model（shift_patterns）から独立する。</p>
 *
 * <p>設計書: review-009 指摘 #2 — CQRS Read Model の一貫性確保</p>
 *
 * <p>対応イベント:
 * <ul>
 *   <li>ShiftPatternDefinedEvent → INSERT（既存があれば UPDATE。べき等）</li>
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
    /** プロジェクトの TZ 方針に従い、Instant → OffsetDateTime 変換は Asia/Tokyo で行う */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Tokyo");

    /** jOOQ DSLContext — 型安全な SQL アクセス */
    private final DSLContext dsl;

    public ShiftPatternSummaryProjector(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ========================================
    // パターン定義イベント → サマリー新規作成（UPSERT）
    // ========================================

    /**
     * シフトパターン定義イベントを処理する
     *
     * <p>shift_pattern_summaries に新規行を UPSERT する。
     * V12 マイグレーションで既存パターンがシード投入されている場合にも対応するため、
     * ON CONFLICT DO UPDATE でべき等性を確保する。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ShiftPatternDefinedEvent event) {
        log.debug("ShiftPatternDefinedEvent受信: patternId={}, name={}",
                event.patternId().value(), event.name().value());

        OffsetDateTime occurredAt = toOffsetDateTime(event.occurredAt());
        OffsetDateTime now = OffsetDateTime.now(APP_ZONE);

        // INSERT ... ON CONFLICT DO UPDATE でべき等に UPSERT する
        dsl.insertInto(SHIFT_PATTERN_SUMMARIES)
                .set(SHIFT_PATTERN_SUMMARIES.SHIFT_PATTERN_ID, event.patternId().value())
                .set(SHIFT_PATTERN_SUMMARIES.NAME, event.name().value())
                .set(SHIFT_PATTERN_SUMMARIES.START_TIME, event.startTime())
                .set(SHIFT_PATTERN_SUMMARIES.END_TIME, event.endTime())
                .set(SHIFT_PATTERN_SUMMARIES.BREAK_MINUTES, event.breakMinutes())
                .set(SHIFT_PATTERN_SUMMARIES.IS_OVERNIGHT, event.isOvernight())
                .set(SHIFT_PATTERN_SUMMARIES.IS_ACTIVE, true)
                .set(SHIFT_PATTERN_SUMMARIES.LAST_EVENT_AT, occurredAt)
                .set(SHIFT_PATTERN_SUMMARIES.EVENT_COUNT, 1)
                .set(SHIFT_PATTERN_SUMMARIES.CREATED_AT, now)
                .set(SHIFT_PATTERN_SUMMARIES.UPDATED_AT, now)
                .set(SHIFT_PATTERN_SUMMARIES.CREATED_BY, SYSTEM_USER)
                .set(SHIFT_PATTERN_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .onConflict(SHIFT_PATTERN_SUMMARIES.SHIFT_PATTERN_ID)
                .doUpdate()
                .set(SHIFT_PATTERN_SUMMARIES.NAME, event.name().value())
                .set(SHIFT_PATTERN_SUMMARIES.START_TIME, event.startTime())
                .set(SHIFT_PATTERN_SUMMARIES.END_TIME, event.endTime())
                .set(SHIFT_PATTERN_SUMMARIES.BREAK_MINUTES, event.breakMinutes())
                .set(SHIFT_PATTERN_SUMMARIES.IS_OVERNIGHT, event.isOvernight())
                .set(SHIFT_PATTERN_SUMMARIES.IS_ACTIVE, true)
                .set(SHIFT_PATTERN_SUMMARIES.LAST_EVENT_AT, occurredAt)
                .set(SHIFT_PATTERN_SUMMARIES.EVENT_COUNT, SHIFT_PATTERN_SUMMARIES.EVENT_COUNT.plus(1))
                .set(SHIFT_PATTERN_SUMMARIES.UPDATED_AT, now)
                .set(SHIFT_PATTERN_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .execute();

        log.debug("シフトパターンサマリー UPSERT 完了: patternId={}", event.patternId().value());
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

        // 有効フラグを false にして監査情報を更新する
        int updated = updateActiveFlag(event.patternId().value(), false, event.occurredAt());
        if (updated == 0) {
            log.warn("シフトパターンサマリーが見つかりません: patternId={}", event.patternId().value());
            return;
        }

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

        // 有効フラグを true にして監査情報を更新する
        int updated = updateActiveFlag(event.patternId().value(), true, event.occurredAt());
        if (updated == 0) {
            log.warn("シフトパターンサマリーが見つかりません: patternId={}", event.patternId().value());
            return;
        }

        log.debug("シフトパターン再有効化反映完了: patternId={}", event.patternId().value());
    }

    // ========================================
    // ヘルパー
    // ========================================

    /**
     * is_active フラグ + 監査情報（last_event_at / event_count / updated_at / updated_by）を一括更新する
     *
     * @return 更新行数（0 なら対象が見つからなかったことを示す）
     */
    private int updateActiveFlag(java.util.UUID patternId, boolean active, Instant occurredAt) {
        return dsl.update(SHIFT_PATTERN_SUMMARIES)
                .set(SHIFT_PATTERN_SUMMARIES.IS_ACTIVE, active)
                .set(SHIFT_PATTERN_SUMMARIES.LAST_EVENT_AT, toOffsetDateTime(occurredAt))
                .set(SHIFT_PATTERN_SUMMARIES.EVENT_COUNT, SHIFT_PATTERN_SUMMARIES.EVENT_COUNT.plus(1))
                .set(SHIFT_PATTERN_SUMMARIES.UPDATED_AT, OffsetDateTime.now(APP_ZONE))
                .set(SHIFT_PATTERN_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .where(SHIFT_PATTERN_SUMMARIES.SHIFT_PATTERN_ID.eq(patternId))
                .execute();
    }

    /**
     * Instant を Asia/Tokyo の OffsetDateTime に変換する
     *
     * <p>PostgreSQL の TIMESTAMP WITH TIME ZONE は内部的に UTC で保存されるため、
     * オフセット値自体は等価だが、プロジェクトの TZ 方針に従って Asia/Tokyo で扱う。</p>
     */
    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(APP_ZONE).toOffsetDateTime();
    }
}
