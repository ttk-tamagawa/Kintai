package com.example.kintai.attendance.infrastructure.repository;

import com.example.kintai.attendance.domain.model.shift.ScheduleStatus;
import com.example.kintai.attendance.domain.model.shift.WeeklySchedule;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleRepository;
import com.example.kintai.attendance.infrastructure.persistence.JpaWeeklyScheduleRepository;
import com.example.kintai.attendance.infrastructure.persistence.entity.WeeklyScheduleJpaEntity;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 週次スケジュールリポジトリ実装 — JPA を使用した DB 永続化
 *
 * <p>ドメイン層の WeeklyScheduleRepository インターフェースを実装する。
 * ドメインモデルの Map&lt;DayOfWeek, ShiftPatternId&gt; と
 * DBの7つの個別カラム（monday_pattern_id〜sunday_pattern_id）を相互変換する。
 * 楽観的ロックは JPA の @Version アノテーションで自動管理される。</p>
 */
@Repository
@Transactional
public class WeeklyScheduleRepositoryImpl implements WeeklyScheduleRepository {

    private final JpaWeeklyScheduleRepository jpaWeeklyScheduleRepo;

    /** 認証未実装のため、監査カラムにはシステムユーザーを設定 */
    private static final String SYSTEM_USER = "system";

    public WeeklyScheduleRepositoryImpl(JpaWeeklyScheduleRepository jpaWeeklyScheduleRepo) {
        this.jpaWeeklyScheduleRepo = jpaWeeklyScheduleRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WeeklySchedule> findById(ScheduleId id) {
        // weekly_schedulesテーブルからIDで検索し、ドメインモデルに変換
        return jpaWeeklyScheduleRepo.findById(id.value())
                .map(this::toDomainModel);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WeeklySchedule> findByEmployeeIdAndWeekStartDate(
            EmployeeId employeeId, LocalDate weekStartDate
    ) {
        // 従業員ID + 週開始日のユニーク制約を利用して検索
        return jpaWeeklyScheduleRepo.findByEmployeeIdAndWeekStartDate(
                employeeId.value(), weekStartDate
        ).map(this::toDomainModel);
    }

    @Override
    public WeeklySchedule save(WeeklySchedule schedule) {
        // ドメインモデル → JPAエンティティに変換（Map → 7カラム）
        WeeklyScheduleJpaEntity entity = toJpaEntity(schedule);

        // weekly_schedulesテーブルに保存（JPAがpersist/mergeを自動判定）
        WeeklyScheduleJpaEntity saved = jpaWeeklyScheduleRepo.saveAndFlush(entity);

        // 保存後のバージョンを反映したドメインオブジェクトを返す
        return WeeklySchedule.reconstruct(
                ScheduleId.of(saved.getId()),
                EmployeeId.of(saved.getEmployeeId()),
                saved.getWeekStartDate(),
                ScheduleStatus.valueOf(saved.getStatus()),
                entityToAssignments(saved),
                saved.getVersion(),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmployeeIdAndWeekStartDate(
            EmployeeId employeeId, LocalDate weekStartDate
    ) {
        return jpaWeeklyScheduleRepo.existsByEmployeeIdAndWeekStartDate(
                employeeId.value(), weekStartDate
        );
    }

    // ========================
    // 変換メソッド
    // ========================

    /**
     * JPAエンティティ → ドメインモデルに変換する
     *
     * <p>7つの曜日カラムをMap&lt;DayOfWeek, ShiftPatternId&gt;に変換する。</p>
     */
    private WeeklySchedule toDomainModel(WeeklyScheduleJpaEntity entity) {
        return WeeklySchedule.reconstruct(
                ScheduleId.of(entity.getId()),
                EmployeeId.of(entity.getEmployeeId()),
                entity.getWeekStartDate(),
                ScheduleStatus.valueOf(entity.getStatus()),
                entityToAssignments(entity),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * ドメインモデル → JPAエンティティに変換する
     *
     * <p>Map&lt;DayOfWeek, ShiftPatternId&gt;を7つの曜日カラムに展開する。</p>
     */
    private WeeklyScheduleJpaEntity toJpaEntity(WeeklySchedule schedule) {
        Map<DayOfWeek, ShiftPatternId> assignments = schedule.getAssignments();

        return new WeeklyScheduleJpaEntity(
                schedule.getId().value(),
                schedule.getEmployeeId().value(),
                schedule.getWeekStartDate(),
                schedule.getStatus().name(),
                getPatternUuid(assignments, DayOfWeek.MONDAY),
                getPatternUuid(assignments, DayOfWeek.TUESDAY),
                getPatternUuid(assignments, DayOfWeek.WEDNESDAY),
                getPatternUuid(assignments, DayOfWeek.THURSDAY),
                getPatternUuid(assignments, DayOfWeek.FRIDAY),
                getPatternUuid(assignments, DayOfWeek.SATURDAY),
                getPatternUuid(assignments, DayOfWeek.SUNDAY),
                schedule.getVersion(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt(),
                SYSTEM_USER,
                SYSTEM_USER
        );
    }

    /**
     * JPAエンティティの7つの曜日カラム → Map&lt;DayOfWeek, ShiftPatternId&gt;に変換する
     *
     * <p>nullでない曜日のみMapに格納する（nullは休みを意味する）。</p>
     */
    private Map<DayOfWeek, ShiftPatternId> entityToAssignments(WeeklyScheduleJpaEntity entity) {
        Map<DayOfWeek, ShiftPatternId> map = new EnumMap<>(DayOfWeek.class);

        // 各曜日のパターンIDがnullでなければMapに追加
        addIfNotNull(map, DayOfWeek.MONDAY, entity.getMondayPatternId());
        addIfNotNull(map, DayOfWeek.TUESDAY, entity.getTuesdayPatternId());
        addIfNotNull(map, DayOfWeek.WEDNESDAY, entity.getWednesdayPatternId());
        addIfNotNull(map, DayOfWeek.THURSDAY, entity.getThursdayPatternId());
        addIfNotNull(map, DayOfWeek.FRIDAY, entity.getFridayPatternId());
        addIfNotNull(map, DayOfWeek.SATURDAY, entity.getSaturdayPatternId());
        addIfNotNull(map, DayOfWeek.SUNDAY, entity.getSundayPatternId());

        return map;
    }

    /**
     * Mapからドメイン型のShiftPatternIdを取得し、UUIDに変換する
     *
     * <p>Mapにキーが存在しない場合はnullを返す（DBカラムにNULLが入る → 休み）。</p>
     */
    private UUID getPatternUuid(Map<DayOfWeek, ShiftPatternId> assignments, DayOfWeek day) {
        ShiftPatternId id = assignments.get(day);
        return id != null ? id.value() : null;
    }

    /**
     * UUIDがnullでなければMapにShiftPatternIdとして追加する
     */
    private void addIfNotNull(Map<DayOfWeek, ShiftPatternId> map, DayOfWeek day, UUID patternId) {
        if (patternId != null) {
            map.put(day, ShiftPatternId.of(patternId));
        }
    }
}
