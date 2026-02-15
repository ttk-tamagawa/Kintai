package com.example.kintai.shared.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 従業員ID — 従業員を一意に識別する型安全なID
 *
 * <p>UUIDをラップすることで、他のID型との混同を防ぐ。
 * 勤怠記録やシフトなど複数の集約で共有して使用される。</p>
 *
 * @param value UUID値（null不可）
 */
public record EmployeeId(UUID value) {

    /**
     * コンパクトコンストラクタ — null値を拒否する
     */
    public EmployeeId {
        Objects.requireNonNull(value, "EmployeeIdのvalueはnullにできません");
    }

    /**
     * 既存のUUIDからIDを生成するファクトリメソッド
     *
     * @param value UUID値
     * @return EmployeeIdインスタンス
     */
    public static EmployeeId of(UUID value) {
        return new EmployeeId(value);
    }

    /**
     * 新規IDを自動生成するファクトリメソッド
     *
     * @return ランダムUUIDで生成されたEmployeeId
     */
    public static EmployeeId generate() {
        return new EmployeeId(UUID.randomUUID());
    }
}
