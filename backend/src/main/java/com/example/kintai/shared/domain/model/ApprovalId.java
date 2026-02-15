package com.example.kintai.shared.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 承認ID — 承認プロセスを一意に識別する型安全なID
 *
 * <p>UUIDをラップすることで、他のID型との混同を防ぐ。
 * 打刻修正や手動勤務登録の承認追跡に使用される。</p>
 *
 * @param value UUID値（null不可）
 */
public record ApprovalId(UUID value) {

    /**
     * コンパクトコンストラクタ — null値を拒否する
     */
    public ApprovalId {
        Objects.requireNonNull(value, "ApprovalIdのvalueはnullにできません");
    }

    /**
     * 既存のUUIDからIDを生成するファクトリメソッド
     *
     * @param value UUID値
     * @return ApprovalIdインスタンス
     */
    public static ApprovalId of(UUID value) {
        return new ApprovalId(value);
    }

    /**
     * 新規IDを自動生成するファクトリメソッド
     *
     * @return ランダムUUIDで生成されたApprovalId
     */
    public static ApprovalId generate() {
        return new ApprovalId(UUID.randomUUID());
    }
}
