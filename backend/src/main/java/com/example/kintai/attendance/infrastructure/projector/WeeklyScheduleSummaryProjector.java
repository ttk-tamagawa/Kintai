package com.example.kintai.attendance.infrastructure.projector;

import com.example.kintai.attendance.domain.model.shift.event.ShiftAssignedEvent;
import com.example.kintai.attendance.domain.model.shift.event.ShiftChangedEvent;
import com.example.kintai.attendance.infrastructure.persistence.entity.ShiftPatternJpaEntity;
import com.example.kintai.attendance.infrastructure.persistence.entity.WeeklyScheduleSummaryJpaEntity;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 週次スケジュールサマリープロジェクター — シフトイベントを購読してweekly_schedule_summariesを更新する
 *
 * <p>シフト割当・変更のドメインイベントを {@code @TransactionalEventListener} で購読し、
 * Read Model（weekly_schedule_summaries）をUPSERTする。
 * パターン名をshift_patternsから取得して非正規化し、JOINなしでカレンダー表示を可能にする。</p>
 *
 * <p>設計書: 30_設計/データベース/シフト.md の「リードモデル同期方式」に対応</p>
 *
 * <p>対応イベント:
 * <ul>
 *   <li>ShiftAssignedEvent → INSERT（新規）またはUPDATE（公開時ステータス更新）</li>
 *   <li>ShiftChangedEvent → UPDATE: 割当変更・ステータスをDRAFTに戻す</li>
 * </ul>
 * </p>
 */
@Component
public class WeeklyScheduleSummaryProjector {

    private static final Logger log = LoggerFactory.getLogger(WeeklyScheduleSummaryProjector.class);
    private static final String SYSTEM_USER = "system";

    private final EntityManager entityManager;

    public WeeklyScheduleSummaryProjector(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // ========================================
    // シフト割当イベント → サマリー新規作成（INSERT）
    // ========================================

    /**
     * シフト割当イベントを処理する
     *
     * <p>weekly_schedule_summariesに新規行を作成し、7曜日分のパターンID・パターン名を設定する。
     * パターン名はshift_patternsテーブルから取得して非正規化する。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ShiftAssignedEvent event) {
        log.debug("ShiftAssignedEvent受信: scheduleId={}, status={}",
                event.scheduleId().value(), event.status());

        // 既存のサマリーを検索する（公開時は既にINSERT済み）
        WeeklyScheduleSummaryJpaEntity existing = entityManager.find(
                WeeklyScheduleSummaryJpaEntity.class, event.scheduleId().value());

        if (existing != null) {
            // 既存サマリーあり → ステータス更新（DRAFT→PUBLISHED等）
            existing.setStatus(event.status().name());
            existing.setLastEventAt(event.occurredAt());
            existing.setEventCount(existing.getEventCount() + 1);
            existing.setUpdatedAt(Instant.now());
            existing.setUpdatedBy(SYSTEM_USER);

            log.debug("サマリーステータス更新完了: scheduleId={}, status={}",
                    event.scheduleId().value(), event.status());
            return;
        }

        // 新規サマリーエンティティを作成する
        WeeklyScheduleSummaryJpaEntity entity = new WeeklyScheduleSummaryJpaEntity(
                event.scheduleId().value(),
                event.employeeId().value(),
                event.weekStartDate(),
                event.status().name()
        );

        // 割当パターンの名前をshift_patternsから一括取得する
        Map<UUID, String> patternNames = lookupPatternNames(event.assignments().values());

        // 7曜日分のパターンID・パターン名をエンティティに設定する
        applyAssignments(entity, event.assignments(), patternNames);

        // 割当日数を計算する（パターンが割り当てられている曜日の数）
        entity.setAssignedDays(event.assignments().size());

        // イベント追跡情報を設定する
        entity.setLastEventAt(event.occurredAt());
        entity.setEventCount(1);

        entityManager.persist(entity);

        log.debug("新規サマリー作成完了: scheduleId={}, assignedDays={}",
                event.scheduleId().value(), event.assignments().size());
    }

    // ========================================
    // シフト変更イベント → サマリー更新（UPDATE）
    // ========================================

    /**
     * シフト変更イベントを処理する
     *
     * <p>既存のweekly_schedule_summariesを更新する。
     * 変更後の全割当でパターンID・パターン名を上書きし、
     * ステータスをDRAFTに戻す（PUBLISHEDだった場合、再公開が必要）。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ShiftChangedEvent event) {
        log.debug("ShiftChangedEvent受信: scheduleId={}", event.scheduleId().value());

        // 既存のサマリーを取得する
        WeeklyScheduleSummaryJpaEntity entity = entityManager.find(
                WeeklyScheduleSummaryJpaEntity.class, event.scheduleId().value());

        if (entity == null) {
            log.warn("サマリーが見つかりません: scheduleId={}", event.scheduleId().value());
            return;
        }

        // 割当パターンの名前をshift_patternsから一括取得する
        Map<UUID, String> patternNames = lookupPatternNames(event.changedDays().values());

        // 7曜日すべてをリセットしてから新しい割当を設定する
        clearAllDayAssignments(entity);
        applyAssignments(entity, event.changedDays(), patternNames);

        // ステータスをDRAFTに戻す（変更したので再公開が必要）
        entity.setStatus("DRAFT");

        // 割当日数を再計算する
        entity.setAssignedDays(event.changedDays().size());

        // イベント追跡情報を更新する
        entity.setLastEventAt(event.occurredAt());
        entity.setEventCount(entity.getEventCount() + 1);
        entity.setUpdatedAt(Instant.now());
        entity.setUpdatedBy(SYSTEM_USER);

        log.debug("サマリー更新完了: scheduleId={}, assignedDays={}",
                event.scheduleId().value(), event.changedDays().size());
    }

    // ========================================
    // パターン名検索
    // ========================================

    /**
     * シフトパターンIDの集合からパターン名を一括取得する
     *
     * <p>shift_patternsテーブルをIN句で検索し、ID→名前のマップを返す。
     * サマリーにパターン名を非正規化するため、割当のたびに最新名を取得する。</p>
     *
     * @param patternIds パターンIDの集合
     * @return パターンID(UUID) → パターン名(String) のマップ
     */
    private Map<UUID, String> lookupPatternNames(Collection<ShiftPatternId> patternIds) {
        // パターンIDをUUIDのリストに変換する
        List<UUID> ids = patternIds.stream()
                .map(ShiftPatternId::value)
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }

        // shift_patternsから名前を一括取得する
        List<ShiftPatternJpaEntity> patterns = entityManager.createQuery(
                "SELECT p FROM ShiftPatternJpaEntity p WHERE p.id IN :ids",
                ShiftPatternJpaEntity.class
        )
        .setParameter("ids", ids)
        .getResultList();

        // ID → 名前のマップに変換する
        return patterns.stream()
                .collect(Collectors.toMap(
                        ShiftPatternJpaEntity::getId,
                        ShiftPatternJpaEntity::getName));
    }

    // ========================================
    // 曜日別割当の設定・クリア
    // ========================================

    /**
     * 曜日別の割当（パターンID・パターン名）をエンティティに設定する
     *
     * <p>ドメインのMap&lt;DayOfWeek, ShiftPatternId&gt;をJPAエンティティの
     * 7曜日×2カラム（ID+名前）に変換して設定する。</p>
     *
     * @param entity       対象のサマリーエンティティ
     * @param assignments  曜日ごとのパターンID割当
     * @param patternNames パターンID→名前のマップ
     */
    private void applyAssignments(WeeklyScheduleSummaryJpaEntity entity,
                                   Map<DayOfWeek, ShiftPatternId> assignments,
                                   Map<UUID, String> patternNames) {
        // 各曜日について、割当があればパターンIDと名前を設定する
        for (Map.Entry<DayOfWeek, ShiftPatternId> entry : assignments.entrySet()) {
            UUID patternId = entry.getValue().value();
            String patternName = patternNames.getOrDefault(patternId, "不明");
            setDayAssignment(entity, entry.getKey(), patternId, patternName);
        }
    }

    /**
     * 指定曜日のパターンID・パターン名をエンティティに設定する
     *
     * <p>DayOfWeekに対応するセッターを呼び出してカラム値を設定する。</p>
     */
    private void setDayAssignment(WeeklyScheduleSummaryJpaEntity entity,
                                   DayOfWeek day, UUID patternId, String patternName) {
        switch (day) {
            case MONDAY -> {
                entity.setMondayPatternId(patternId);
                entity.setMondayPatternName(patternName);
            }
            case TUESDAY -> {
                entity.setTuesdayPatternId(patternId);
                entity.setTuesdayPatternName(patternName);
            }
            case WEDNESDAY -> {
                entity.setWednesdayPatternId(patternId);
                entity.setWednesdayPatternName(patternName);
            }
            case THURSDAY -> {
                entity.setThursdayPatternId(patternId);
                entity.setThursdayPatternName(patternName);
            }
            case FRIDAY -> {
                entity.setFridayPatternId(patternId);
                entity.setFridayPatternName(patternName);
            }
            case SATURDAY -> {
                entity.setSaturdayPatternId(patternId);
                entity.setSaturdayPatternName(patternName);
            }
            case SUNDAY -> {
                entity.setSundayPatternId(patternId);
                entity.setSundayPatternName(patternName);
            }
        }
    }

    /**
     * 7曜日すべてのパターンID・パターン名をnullにリセットする
     *
     * <p>シフト変更時に全曜日をクリアしてから新しい割当を適用するために使用する。
     * 割当がない曜日（休み）はnullのまま残る。</p>
     */
    private void clearAllDayAssignments(WeeklyScheduleSummaryJpaEntity entity) {
        entity.setMondayPatternId(null);
        entity.setMondayPatternName(null);
        entity.setTuesdayPatternId(null);
        entity.setTuesdayPatternName(null);
        entity.setWednesdayPatternId(null);
        entity.setWednesdayPatternName(null);
        entity.setThursdayPatternId(null);
        entity.setThursdayPatternName(null);
        entity.setFridayPatternId(null);
        entity.setFridayPatternName(null);
        entity.setSaturdayPatternId(null);
        entity.setSaturdayPatternName(null);
        entity.setSundayPatternId(null);
        entity.setSundayPatternName(null);
    }
}
