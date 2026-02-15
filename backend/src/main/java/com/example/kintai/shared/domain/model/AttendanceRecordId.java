package com.example.kintai.shared.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 勤怠記録ID — 勤怠記録集約を一意に識別する型安全なID
 *
 * <p>UUIDをラップすることで、他のID型（EmployeeId等）との混同を防ぐ。
 * recordにより不変性・等価性・hashCode・toStringが自動生成される。</p>
 *
 * @param value UUID値（null不可）
 */
public record AttendanceRecordId(UUID value) {

    /**
     * コンパクトコンストラクタ — null値を拒否する
     */
    public AttendanceRecordId {
        Objects.requireNonNull(value, "AttendanceRecordIdのvalueはnullにできません");
    }

    /**
     * 既存のUUIDからIDを生成するファクトリメソッド
     *
     * @param value UUID値
     * @return AttendanceRecordIdインスタンス
     */
    public static AttendanceRecordId of(UUID value) {
        return new AttendanceRecordId(value);
    }

    /**
     * 新規IDを自動生成するファクトリメソッド
     *
     * @return ランダムUUIDで生成されたAttendanceRecordId
     */
    public static AttendanceRecordId generate() {
        return new AttendanceRecordId(UUID.randomUUID());
    }
}
