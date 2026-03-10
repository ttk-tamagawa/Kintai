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
public record EmployeeId(String value) {

    /**
     * コンパクトコンストラクタ — null・空文字を拒否する
     */
    public EmployeeId {
        Objects.requireNonNull(value, "EmployeeIdのvalueはnullにできません");
        if (value.isBlank()) {
            throw new IllegalArgumentException("EmployeeIdのvalueは空にできません");
        }
    }

    /**
     * 文字列からIDを生成するファクトリメソッド
     *
     * @param value ID文字列（UUID形式または任意の文字列）
     * @return EmployeeIdインスタンス
     */
    public static EmployeeId of(String value) {
        return new EmployeeId(value);
    }

    /**
     * UUIDからIDを生成するファクトリメソッド（後方互換）
     *
     * @param value UUID値
     * @return EmployeeIdインスタンス
     */
    public static EmployeeId of(UUID value) {
        return new EmployeeId(value.toString());
    }

    /**
     * 新規IDを自動生成するファクトリメソッド
     *
     * @return ランダムUUIDで生成されたEmployeeId
     */
    public static EmployeeId generate() {
        return new EmployeeId(UUID.randomUUID().toString());
    }
}
