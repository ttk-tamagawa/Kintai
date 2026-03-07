package com.example.kintai.attendance.infrastructure.repository;

import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.model.AttendanceStatus;
import com.example.kintai.attendance.domain.model.ClockEntry;
import com.example.kintai.attendance.domain.model.ClockSource;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.attendance.domain.model.ClockType;
import com.example.kintai.attendance.domain.model.OvertimeDuration;
import com.example.kintai.attendance.domain.model.WorkDate;
import com.example.kintai.attendance.domain.model.WorkDuration;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.attendance.infrastructure.persistence.JpaAttendanceRepository;
import com.example.kintai.attendance.infrastructure.persistence.entity.AttendanceJpaEntity;
import com.example.kintai.attendance.infrastructure.persistence.entity.ClockEntryJpaEntity;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 勤怠記録リポジトリ実装 — JPA/EntityManagerを使用したDB永続化
 *
 * <p>ドメイン層のAttendanceRecordRepositoryインターフェースを実装する。
 * 集約（AttendanceRecord）をattendancesテーブルに、
 * 打刻エントリをclock_entriesテーブルにそれぞれ保存する。
 * 楽観的ロックはJPAの@Versionアノテーションで自動管理される。</p>
 */
@Repository
@Transactional
public class AttendanceRecordRepositoryImpl implements AttendanceRecordRepository {

    private final JpaAttendanceRepository jpaAttendanceRepo;

    @PersistenceContext
    private final EntityManager entityManager;

    /** 認証未実装のため、監査カラムにはシステムユーザーを設定 */
    private static final String SYSTEM_USER = "system";

    public AttendanceRecordRepositoryImpl(
            JpaAttendanceRepository jpaAttendanceRepo,
            EntityManager entityManager
    ) {
        this.jpaAttendanceRepo = jpaAttendanceRepo;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttendanceRecord> findById(AttendanceRecordId id) {
        // attendancesテーブルからJPAエンティティを検索
        return jpaAttendanceRepo.findById(id.value())
                .map(this::toDomainRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(EmployeeId employeeId, WorkDate workDate) {
        // 従業員ID + 勤務日のユニーク制約を利用して検索
        return jpaAttendanceRepo.findByEmployeeIdAndWorkDate(
                employeeId.value(), workDate.value()
        ).map(this::toDomainRecord);
    }

    @Override
    public AttendanceRecord save(AttendanceRecord record) {
        // ドメインモデル → JPAエンティティに変換
        AttendanceJpaEntity entity = toJpaEntity(record);

        // attendancesテーブルに保存（JPAがpersist/mergeを自動判定）
        AttendanceJpaEntity saved = jpaAttendanceRepo.saveAndFlush(entity);

        // 新しい打刻エントリをclock_entriesテーブルに追記
        saveNewClockEntries(record);

        // 保存後のバージョンを反映したドメインオブジェクトを返す
        return AttendanceRecord.reconstruct(
                record.getId(),
                record.getEmployeeId(),
                record.getWorkDate(),
                record.getShiftPatternId(),
                record.getStatus(),
                record.getClockEntries(),
                record.getWorkDuration(),
                record.getOvertimeDuration(),
                saved.getVersion(),
                record.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmployeeIdAndWorkDate(EmployeeId employeeId, WorkDate workDate) {
        return jpaAttendanceRepo.existsByEmployeeIdAndWorkDate(
                employeeId.value(), workDate.value()
        );
    }

    // ========================
    // 変換メソッド
    // ========================

    /**
     * JPAエンティティ → ドメインモデルに変換する
     *
     * <p>attendancesテーブルのデータとclock_entriesテーブルのデータを
     * 組み合わせてAttendanceRecordドメインオブジェクトを復元する。</p>
     */
    private AttendanceRecord toDomainRecord(AttendanceJpaEntity entity) {
        // 打刻エントリをclock_entriesテーブルから取得（時刻昇順）
        List<ClockEntry> clockEntries = loadClockEntries(entity.getId());

        // ShiftPatternIdのnull安全な変換
        ShiftPatternId shiftPatternId = entity.getShiftPatternId() != null
                ? ShiftPatternId.of(entity.getShiftPatternId())
                : null;

        // ドメインオブジェクトを復元（勤務時間はゼロで初期化、必要時に再計算）
        return AttendanceRecord.reconstruct(
                AttendanceRecordId.of(entity.getId()),
                EmployeeId.of(entity.getEmployeeId()),
                new WorkDate(entity.getWorkDate()),
                shiftPatternId,
                AttendanceStatus.valueOf(entity.getStatus()),
                clockEntries,
                WorkDuration.zero(),
                OvertimeDuration.zero(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * ドメインモデル → JPAエンティティに変換する
     */
    private AttendanceJpaEntity toJpaEntity(AttendanceRecord record) {
        // ShiftPatternIdのnull安全な変換
        UUID shiftPatternId = record.getShiftPatternId() != null
                ? record.getShiftPatternId().value()
                : null;

        return new AttendanceJpaEntity(
                record.getId().value(),
                record.getEmployeeId().value(),
                record.getWorkDate().value(),
                shiftPatternId,
                record.getStatus().name(),
                record.getVersion(),
                record.getCreatedAt(),
                record.getUpdatedAt(),
                SYSTEM_USER,
                SYSTEM_USER
        );
    }

    /**
     * 勤怠記録IDに紐づく打刻エントリをDBから取得する
     */
    @SuppressWarnings("unchecked")
    private List<ClockEntry> loadClockEntries(UUID attendanceId) {
        // clock_entriesテーブルから時刻昇順で取得
        List<ClockEntryJpaEntity> entities = entityManager.createQuery(
                "SELECT c FROM ClockEntryJpaEntity c WHERE c.attendanceId = :attendanceId ORDER BY c.time ASC"
        ).setParameter("attendanceId", attendanceId).getResultList();

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

    /**
     * 新しい打刻エントリのみをclock_entriesテーブルに追記する
     *
     * <p>打刻エントリは追記のみ（INSERT ONLY）のため、
     * DB上の既存件数と集約内の件数を比較し、差分のみINSERTする。</p>
     */
    private void saveNewClockEntries(AttendanceRecord record) {
        UUID attendanceId = record.getId().value();

        // DB上の既存打刻エントリ数を取得
        long existingCount = (long) entityManager.createQuery(
                "SELECT COUNT(c) FROM ClockEntryJpaEntity c WHERE c.attendanceId = :attendanceId"
        ).setParameter("attendanceId", attendanceId).getSingleResult();

        List<ClockEntry> allEntries = record.getClockEntries();

        // 既存件数以降のエントリが「新規追加分」
        for (int i = (int) existingCount; i < allEntries.size(); i++) {
            ClockEntry entry = allEntries.get(i);
            Instant now = Instant.now();

            ClockEntryJpaEntity clockEntity = new ClockEntryJpaEntity(
                    UUID.randomUUID(),
                    attendanceId,
                    entry.type().name(),
                    entry.time().value(),
                    entry.source().name(),
                    null,
                    now,
                    SYSTEM_USER
            );
            entityManager.persist(clockEntity);
        }
    }
}
