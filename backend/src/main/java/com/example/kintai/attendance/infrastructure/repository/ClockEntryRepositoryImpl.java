package com.example.kintai.attendance.infrastructure.repository;

import com.example.kintai.attendance.domain.model.ClockEntry;
import com.example.kintai.attendance.domain.model.ClockSource;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.attendance.domain.model.ClockType;
import com.example.kintai.attendance.domain.repository.ClockEntryRepository;
import com.example.kintai.attendance.infrastructure.persistence.entity.ClockEntryJpaEntity;
import com.example.kintai.shared.domain.model.AttendanceRecordId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 打刻エントリリポジトリ実装 — EntityManagerを使用した追記専用永続化
 *
 * <p>打刻ログをclock_entriesテーブルにINSERTする。
 * UPDATE/DELETEは行わない（打刻ログは不変）。
 * 修正時はsource=CORRECTIONの新しい行が追加される設計。</p>
 */
@Repository
@Transactional
public class ClockEntryRepositoryImpl implements ClockEntryRepository {

    @PersistenceContext
    private final EntityManager entityManager;

    /** 認証未実装のため、監査カラムにはシステムユーザーを設定 */
    private static final String SYSTEM_USER = "system";

    public ClockEntryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void appendAll(AttendanceRecordId attendanceId, List<ClockEntry> entries) {
        UUID attId = attendanceId.value();
        Instant now = Instant.now();

        // 各打刻エントリをINSERT（追記のみ）
        for (ClockEntry entry : entries) {
            ClockEntryJpaEntity entity = new ClockEntryJpaEntity(
                    UUID.randomUUID(),
                    attId,
                    entry.type().name(),
                    entry.time().value(),
                    entry.source().name(),
                    null,
                    now,
                    SYSTEM_USER
            );
            entityManager.persist(entity);
        }
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ClockEntry> findByAttendanceId(AttendanceRecordId attendanceId) {
        // clock_entriesテーブルから時刻昇順で取得
        List<ClockEntryJpaEntity> entities = entityManager.createQuery(
                "SELECT c FROM ClockEntryJpaEntity c WHERE c.attendanceId = :attendanceId ORDER BY c.time ASC"
        ).setParameter("attendanceId", attendanceId.value()).getResultList();

        // JPAエンティティ → ドメインVO（ClockEntry）に変換
        List<ClockEntry> result = new ArrayList<>();
        for (ClockEntryJpaEntity e : entities) {
            result.add(new ClockEntry(
                    ClockType.valueOf(e.getType()),
                    new ClockTime(e.getTime()),
                    ClockSource.valueOf(e.getSource())
            ));
        }
        return result;
    }
}
