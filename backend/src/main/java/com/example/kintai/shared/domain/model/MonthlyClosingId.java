package com.example.kintai.shared.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 月次締めID — 月次締め処理を一意に識別する型安全なID
 *
 * <p>UUIDをラップすることで、他のID型（AttendanceRecordId等）との混同を防ぐ。
 * recordにより不変性・等価性・hashCode・toStringが自動生成される。</p>
 *
 * @param value UUID値（null不可）
 */
public record MonthlyClosingId(UUID value) {

    /**
     * コンパクトコンストラクタ — null値を拒否する
     */
    public MonthlyClosingId {
        Objects.requireNonNull(value, "MonthlyClosingIdのvalueはnullにできません");
    }

    /**
     * 既存のUUIDからIDを生成するファクトリメソッド
     *
     * @param value UUID値
     * @return MonthlyClosingIdインスタンス
     */
    public static MonthlyClosingId of(UUID value) {
        return new MonthlyClosingId(value);
    }

    /**
     * 新規IDを自動生成するファクトリメソッド
     *
     * @return ランダムUUIDで生成されたMonthlyClosingId
     */
    public static MonthlyClosingId generate() {
        return new MonthlyClosingId(UUID.randomUUID());
    }
}
