package com.example.kintai.attendance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 勤怠記録JPAエンティティ — attendancesテーブルのマッピング
 *
 * <p>Write Model（書き込みモデル）のテーブル。
 * ドメインモデル（AttendanceRecord）とDBの間の橋渡し役。
 * 楽観的ロック（@Version）でバージョン管理を行う。</p>
 */
@Entity
@Table(name = "attendances")
public class AttendanceJpaEntity {

    /** 勤怠記録ID（UUID主キー） */
    @Id
    @Column(name = "id")
    private UUID id;

    /** 従業員ID（VARCHAR(36): employees テーブルに合わせた文字列型） */
    @Column(name = "employee_id", nullable = false, length = 36)
    private String employeeId;

    /** 勤務日 */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** シフトパターンID（nullable: シフト未割当の場合） */
    @Column(name = "shift_pattern_id")
    private UUID shiftPatternId;

    /** 勤怠ステータス（NOT_CLOCKED, CLOCKED_IN, CLOCKED_OUT, FINALIZED） */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 楽観的ロック用バージョン — JPAが自動インクリメントする */
    @Version
    @Column(name = "version", nullable = false)
    private int version;

    /** 作成日時（INSERT時のみ設定、以降は更新しない） */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 更新日時 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 作成者（INSERT時のみ設定、以降は更新しない） */
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    /** 更新者 */
    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    /** 論理削除日時（nullなら有効） */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** JPA必須の引数なしコンストラクタ */
    protected AttendanceJpaEntity() {}

    /**
     * 全フィールド指定コンストラクタ — リポジトリ実装から使用する
     */
    public AttendanceJpaEntity(
            UUID id, String employeeId, LocalDate workDate, UUID shiftPatternId,
            String status, int version, Instant createdAt, Instant updatedAt,
            String createdBy, String updatedBy
    ) {
        this.id = id;
        this.employeeId = employeeId;
        this.workDate = workDate;
        this.shiftPatternId = shiftPatternId;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    // ゲッター

    public UUID getId() { return id; }
    public String getEmployeeId() { return employeeId; }
    public LocalDate getWorkDate() { return workDate; }
    public UUID getShiftPatternId() { return shiftPatternId; }
    public String getStatus() { return status; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getDeletedAt() { return deletedAt; }
}
