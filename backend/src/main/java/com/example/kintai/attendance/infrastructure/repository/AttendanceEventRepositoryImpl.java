package com.example.kintai.attendance.infrastructure.repository;

import com.example.kintai.attendance.domain.repository.AttendanceEventRepository;
import com.example.kintai.attendance.infrastructure.persistence.entity.AttendanceEventJpaEntity;
import com.example.kintai.shared.domain.model.AttendanceRecordId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 勤怠イベントリポジトリ実装 — EntityManagerを使用した追記専用永続化
 *
 * <p>ドメインイベントをattendance_eventsテーブルにINSERTする。
 * UPDATE/DELETEは行わない（イベントログは不変）。
 * アプリケーション層がドメインイベントをJSON文字列に変換してから渡す。</p>
 */
@Repository
@Transactional
public class AttendanceEventRepositoryImpl implements AttendanceEventRepository {

    @PersistenceContext
    private final EntityManager entityManager;

    /** 認証未実装のため、監査カラムにはシステムユーザーを設定 */
    private static final String SYSTEM_USER = "system";

    public AttendanceEventRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void append(
            AttendanceRecordId attendanceId,
            String eventType,
            String payloadJson,
            Instant occurredAt
    ) {
        Instant now = Instant.now();

        // イベントエンティティを作成してINSERT
        AttendanceEventJpaEntity entity = new AttendanceEventJpaEntity(
                UUID.randomUUID(),
                attendanceId.value(),
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
