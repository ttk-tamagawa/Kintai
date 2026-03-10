package com.example.kintai.attendance.infrastructure.repository;

import com.example.kintai.attendance.domain.repository.WeeklyScheduleEventRepository;
import com.example.kintai.attendance.infrastructure.persistence.entity.WeeklyScheduleEventJpaEntity;
import com.example.kintai.shared.domain.model.ScheduleId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * スケジュールイベントリポジトリ実装 — EntityManager を使用した追記専用永続化
 *
 * <p>ドメインイベントを weekly_schedule_events テーブルに INSERT する。
 * UPDATE/DELETE は行わない（イベントログは不変）。
 * アプリケーション層がドメインイベントを JSON 文字列に変換してから渡す。</p>
 */
@Repository
@Transactional
public class WeeklyScheduleEventRepositoryImpl implements WeeklyScheduleEventRepository {

    @PersistenceContext
    private final EntityManager entityManager;

    /** 認証未実装のため、監査カラムにはシステムユーザーを設定 */
    private static final String SYSTEM_USER = "system";

    public WeeklyScheduleEventRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void append(
            ScheduleId scheduleId,
            String eventType,
            String payloadJson,
            Instant occurredAt
    ) {
        Instant now = Instant.now();

        // イベントエンティティを作成して weekly_schedule_events テーブルに INSERT
        WeeklyScheduleEventJpaEntity entity = new WeeklyScheduleEventJpaEntity(
                UUID.randomUUID(),
                scheduleId.value(),
                eventType,
                payloadJson,
                occurredAt,
                null,
                now,
                SYSTEM_USER
        );

        entityManager.persist(entity);
    }
}
