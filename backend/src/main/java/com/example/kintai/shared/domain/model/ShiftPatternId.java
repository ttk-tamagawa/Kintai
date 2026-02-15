package com.example.kintai.shared.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * シフトパターンID — シフトパターンを一意に識別する型安全なID
 *
 * <p>UUIDをラップすることで、他のID型との混同を防ぐ。
 * 勤怠記録集約でシフト参照に使用される。</p>
 *
 * @param value UUID値（null不可）
 */
public record ShiftPatternId(UUID value) {

    /**
     * コンパクトコンストラクタ — null値を拒否する
     */
    public ShiftPatternId {
        Objects.requireNonNull(value, "ShiftPatternIdのvalueはnullにできません");
    }

    /**
     * 既存のUUIDからIDを生成するファクトリメソッド
     *
     * @param value UUID値
     * @return ShiftPatternIdインスタンス
     */
    public static ShiftPatternId of(UUID value) {
        return new ShiftPatternId(value);
    }

    /**
     * 新規IDを自動生成するファクトリメソッド
     *
     * @return ランダムUUIDで生成されたShiftPatternId
     */
    public static ShiftPatternId generate() {
        return new ShiftPatternId(UUID.randomUUID());
    }
}
