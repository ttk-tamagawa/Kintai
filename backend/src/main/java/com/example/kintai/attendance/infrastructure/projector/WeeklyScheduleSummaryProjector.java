package com.example.kintai.attendance.infrastructure.projector;

import com.example.kintai.attendance.domain.model.shift.event.SchedulePublishedEvent;
import com.example.kintai.attendance.domain.model.shift.event.ScheduleUnpublishedEvent;
import com.example.kintai.attendance.domain.model.shift.event.ShiftAssignedEvent;
import com.example.kintai.attendance.domain.model.shift.event.ShiftChangedEvent;
import com.example.kintai.attendance.infrastructure.jooq.generated.tables.records.WeeklyScheduleSummariesRecord;
import com.example.kintai.attendance.infrastructure.persistence.entity.ShiftPatternJpaEntity;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import jakarta.persistence.EntityManager;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.TableField;
import org.jooq.UpdateSetMoreStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.example.kintai.attendance.infrastructure.jooq.generated.Tables.WEEKLY_SCHEDULE_SUMMARIES;

/**
 * 週次スケジュールサマリープロジェクター — シフトイベントを購読してweekly_schedule_summariesを更新する
 *
 * <p>シフト割当・変更のドメインイベントを {@code @TransactionalEventListener} で購読し、
 * Read Model（weekly_schedule_summaries）を jOOQ で UPSERT / UPDATE する。
 * パターン名を shift_patterns から取得して非正規化し、JOINなしでカレンダー表示を可能にする。</p>
 *
 * <p>shift_patterns は Write Model（集約）のテーブルのため、パターン名取得は JPA EntityManager を使用する。
 * Read Model（weekly_schedule_summaries）の更新は jOOQ で型安全に行う。</p>
 *
 * <p>設計書: 30_設計/データベース/シフト.md の「リードモデル同期方式」に対応</p>
 *
 * <p>対応イベント:
 * <ul>
 *   <li>ShiftAssignedEvent → INSERT: 新規サマリー作成</li>
 *   <li>ShiftChangedEvent → UPDATE: 割当変更・ステータスをDRAFTに戻す</li>
 *   <li>SchedulePublishedEvent → UPDATE: ステータスをPUBLISHEDに更新</li>
 *   <li>ScheduleUnpublishedEvent → UPDATE: ステータスをDRAFTに更新</li>
 * </ul>
 * </p>
 */
@Component
public class WeeklyScheduleSummaryProjector {

    private static final Logger log = LoggerFactory.getLogger(WeeklyScheduleSummaryProjector.class);
    private static final String SYSTEM_USER = "system";
    /** プロジェクトの TZ 方針に従い、Instant → OffsetDateTime 変換は Asia/Tokyo で行う */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Tokyo");

    /** パターン名が shift_patterns から取得できなかった場合のフォールバック表示名 */
    private static final String UNKNOWN_PATTERN_NAME = "不明";

    /** Write Model（shift_patterns）のパターン名取得に使用 */
    private final EntityManager entityManager;

    /** Read Model（weekly_schedule_summaries）の操作に使用 */
    private final DSLContext dsl;

    public WeeklyScheduleSummaryProjector(EntityManager entityManager, DSLContext dsl) {
        this.entityManager = entityManager;
        this.dsl = dsl;
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

        // 割当パターンの名前をshift_patternsから一括取得する
        Map<UUID, String> patternNames = lookupPatternNames(event.assignments().values());

        OffsetDateTime occurredAt = toOffsetDateTime(event.occurredAt());
        OffsetDateTime now = OffsetDateTime.now(APP_ZONE);

        // INSERT 文を構築 — 基本カラムとメタ情報を set する
        InsertSetMoreStep<WeeklyScheduleSummariesRecord> insert = dsl.insertInto(WEEKLY_SCHEDULE_SUMMARIES)
                .set(WEEKLY_SCHEDULE_SUMMARIES.WEEKLY_SCHEDULE_ID, event.scheduleId().value())
                .set(WEEKLY_SCHEDULE_SUMMARIES.EMPLOYEE_ID, event.employeeId().value())
                .set(WEEKLY_SCHEDULE_SUMMARIES.WEEK_START_DATE, event.weekStartDate())
                .set(WEEKLY_SCHEDULE_SUMMARIES.STATUS, event.status().name())
                .set(WEEKLY_SCHEDULE_SUMMARIES.ASSIGNED_DAYS, event.assignments().size())
                .set(WEEKLY_SCHEDULE_SUMMARIES.LAST_EVENT_AT, occurredAt)
                .set(WEEKLY_SCHEDULE_SUMMARIES.EVENT_COUNT, 1)
                .set(WEEKLY_SCHEDULE_SUMMARIES.CREATED_AT, now)
                .set(WEEKLY_SCHEDULE_SUMMARIES.UPDATED_AT, now)
                .set(WEEKLY_SCHEDULE_SUMMARIES.CREATED_BY, SYSTEM_USER)
                .set(WEEKLY_SCHEDULE_SUMMARIES.UPDATED_BY, SYSTEM_USER);

        // 7曜日分のパターンID・パターン名を set する（割当がない曜日は null のまま残る）
        for (DayOfWeek day : DayOfWeek.values()) {
            ShiftPatternId assigned = event.assignments().get(day);
            UUID patternId = assigned != null ? assigned.value() : null;
            String patternName = patternId != null
                    ? patternNames.getOrDefault(patternId, UNKNOWN_PATTERN_NAME)
                    : null;
            insert = insert
                    .set(patternIdField(day), patternId)
                    .set(patternNameField(day), patternName);
        }

        insert.execute();

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
     * 7曜日すべてを一度 null に上書きしてから新しい割当を設定するため、
     * 割当から外れた曜日は自動的にクリアされる。
     * ステータスはDRAFTに戻す（PUBLISHEDだった場合、再公開が必要）。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ShiftChangedEvent event) {
        log.debug("ShiftChangedEvent受信: scheduleId={}", event.scheduleId().value());

        // 割当パターンの名前をshift_patternsから一括取得する
        Map<UUID, String> patternNames = lookupPatternNames(event.changedDays().values());

        OffsetDateTime occurredAt = toOffsetDateTime(event.occurredAt());
        OffsetDateTime now = OffsetDateTime.now(APP_ZONE);

        // UPDATE 文を構築 — まずはメタ情報を set する
        UpdateSetMoreStep<WeeklyScheduleSummariesRecord> update = dsl.update(WEEKLY_SCHEDULE_SUMMARIES)
                .set(WEEKLY_SCHEDULE_SUMMARIES.STATUS, "DRAFT")
                .set(WEEKLY_SCHEDULE_SUMMARIES.ASSIGNED_DAYS, event.changedDays().size())
                .set(WEEKLY_SCHEDULE_SUMMARIES.LAST_EVENT_AT, occurredAt)
                .set(WEEKLY_SCHEDULE_SUMMARIES.EVENT_COUNT, WEEKLY_SCHEDULE_SUMMARIES.EVENT_COUNT.plus(1))
                .set(WEEKLY_SCHEDULE_SUMMARIES.UPDATED_AT, now)
                .set(WEEKLY_SCHEDULE_SUMMARIES.UPDATED_BY, SYSTEM_USER);

        // 7曜日分のパターンID・パターン名を set する（割当がない曜日は明示的に null にする）
        for (DayOfWeek day : DayOfWeek.values()) {
            ShiftPatternId assigned = event.changedDays().get(day);
            UUID patternId = assigned != null ? assigned.value() : null;
            String patternName = patternId != null
                    ? patternNames.getOrDefault(patternId, UNKNOWN_PATTERN_NAME)
                    : null;
            update = update
                    .set(patternIdField(day), patternId)
                    .set(patternNameField(day), patternName);
        }

        int updated = update
                .where(WEEKLY_SCHEDULE_SUMMARIES.WEEKLY_SCHEDULE_ID.eq(event.scheduleId().value()))
                .execute();

        if (updated == 0) {
            log.warn("サマリーが見つかりません: scheduleId={}", event.scheduleId().value());
            return;
        }

        log.debug("サマリー更新完了: scheduleId={}, assignedDays={}",
                event.scheduleId().value(), event.changedDays().size());
    }

    // ========================================
    // スケジュール公開イベント → ステータスをPUBLISHEDに更新
    // ========================================

    /**
     * スケジュール公開イベントを処理する
     *
     * <p>weekly_schedule_summariesのステータスをPUBLISHEDに更新する。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(SchedulePublishedEvent event) {
        log.debug("SchedulePublishedEvent受信: scheduleId={}", event.scheduleId().value());

        int updated = updateStatus(event.scheduleId().value(), "PUBLISHED", event.occurredAt());
        if (updated == 0) {
            log.warn("サマリーが見つかりません: scheduleId={}", event.scheduleId().value());
            return;
        }

        log.debug("サマリー公開完了: scheduleId={}", event.scheduleId().value());
    }

    // ========================================
    // スケジュール非公開イベント → ステータスをDRAFTに更新
    // ========================================

    /**
     * スケジュール非公開イベントを処理する
     *
     * <p>weekly_schedule_summariesのステータスをDRAFTに更新する。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ScheduleUnpublishedEvent event) {
        log.debug("ScheduleUnpublishedEvent受信: scheduleId={}", event.scheduleId().value());

        int updated = updateStatus(event.scheduleId().value(), "DRAFT", event.occurredAt());
        if (updated == 0) {
            log.warn("サマリーが見つかりません: scheduleId={}", event.scheduleId().value());
            return;
        }

        log.debug("サマリー非公開完了: scheduleId={}", event.scheduleId().value());
    }

    // ========================================
    // ヘルパー
    // ========================================

    /**
     * ステータスと監査情報を一括更新する
     *
     * @return 更新行数（0 なら対象が見つからなかったことを示す）
     */
    private int updateStatus(UUID scheduleId, String newStatus, Instant occurredAt) {
        return dsl.update(WEEKLY_SCHEDULE_SUMMARIES)
                .set(WEEKLY_SCHEDULE_SUMMARIES.STATUS, newStatus)
                .set(WEEKLY_SCHEDULE_SUMMARIES.LAST_EVENT_AT, toOffsetDateTime(occurredAt))
                .set(WEEKLY_SCHEDULE_SUMMARIES.EVENT_COUNT, WEEKLY_SCHEDULE_SUMMARIES.EVENT_COUNT.plus(1))
                .set(WEEKLY_SCHEDULE_SUMMARIES.UPDATED_AT, OffsetDateTime.now(APP_ZONE))
                .set(WEEKLY_SCHEDULE_SUMMARIES.UPDATED_BY, SYSTEM_USER)
                .where(WEEKLY_SCHEDULE_SUMMARIES.WEEKLY_SCHEDULE_ID.eq(scheduleId))
                .execute();
    }

    /**
     * シフトパターンIDの集合からパターン名を一括取得する
     *
     * <p>shift_patterns は Write Model 集約のテーブルのため、JPA（EntityManager）で参照する。
     * IDが重複する可能性もあるため、toMap の merge 関数で重複キーを許容する。</p>
     *
     * @param patternIds パターンIDの集合
     * @return パターンID(UUID) → パターン名(String) のマップ
     */
    private Map<UUID, String> lookupPatternNames(Collection<ShiftPatternId> patternIds) {
        List<UUID> ids = patternIds.stream()
                .map(ShiftPatternId::value)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }

        List<ShiftPatternJpaEntity> patterns = entityManager.createQuery(
                        "SELECT p FROM ShiftPatternJpaEntity p WHERE p.id IN :ids",
                        ShiftPatternJpaEntity.class)
                .setParameter("ids", ids)
                .getResultList();

        return patterns.stream()
                .collect(Collectors.toMap(
                        ShiftPatternJpaEntity::getId,
                        ShiftPatternJpaEntity::getName,
                        (a, b) -> a));
    }

    /**
     * 曜日 → パターンID カラムのマッピング
     */
    private static TableField<WeeklyScheduleSummariesRecord, UUID> patternIdField(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> WEEKLY_SCHEDULE_SUMMARIES.MONDAY_PATTERN_ID;
            case TUESDAY -> WEEKLY_SCHEDULE_SUMMARIES.TUESDAY_PATTERN_ID;
            case WEDNESDAY -> WEEKLY_SCHEDULE_SUMMARIES.WEDNESDAY_PATTERN_ID;
            case THURSDAY -> WEEKLY_SCHEDULE_SUMMARIES.THURSDAY_PATTERN_ID;
            case FRIDAY -> WEEKLY_SCHEDULE_SUMMARIES.FRIDAY_PATTERN_ID;
            case SATURDAY -> WEEKLY_SCHEDULE_SUMMARIES.SATURDAY_PATTERN_ID;
            case SUNDAY -> WEEKLY_SCHEDULE_SUMMARIES.SUNDAY_PATTERN_ID;
        };
    }

    /**
     * 曜日 → パターン名カラムのマッピング
     */
    private static TableField<WeeklyScheduleSummariesRecord, String> patternNameField(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> WEEKLY_SCHEDULE_SUMMARIES.MONDAY_PATTERN_NAME;
            case TUESDAY -> WEEKLY_SCHEDULE_SUMMARIES.TUESDAY_PATTERN_NAME;
            case WEDNESDAY -> WEEKLY_SCHEDULE_SUMMARIES.WEDNESDAY_PATTERN_NAME;
            case THURSDAY -> WEEKLY_SCHEDULE_SUMMARIES.THURSDAY_PATTERN_NAME;
            case FRIDAY -> WEEKLY_SCHEDULE_SUMMARIES.FRIDAY_PATTERN_NAME;
            case SATURDAY -> WEEKLY_SCHEDULE_SUMMARIES.SATURDAY_PATTERN_NAME;
            case SUNDAY -> WEEKLY_SCHEDULE_SUMMARIES.SUNDAY_PATTERN_NAME;
        };
    }

    /**
     * Instant を Asia/Tokyo の OffsetDateTime に変換する
     */
    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(APP_ZONE).toOffsetDateTime();
    }
}
