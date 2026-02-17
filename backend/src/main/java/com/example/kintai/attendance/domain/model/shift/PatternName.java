package com.example.kintai.attendance.domain.model.shift;

import java.util.Objects;

/**
 * パターン名VO — シフトパターンの名前を表す値オブジェクト
 *
 * <p>パターン名は2〜20文字の制約を持つ。
 * システム全体で一意である必要がある（INV-SH-001）。
 * 一意性チェックはリポジトリ層で行う。</p>
 *
 * @param value パターン名文字列（2〜20文字）
 */
public record PatternName(String value) {

    /** パターン名の最小文字数 */
    private static final int MIN_LENGTH = 2;

    /** パターン名の最大文字数 */
    private static final int MAX_LENGTH = 20;

    /**
     * コンパクトコンストラクタ — nullチェックと文字数バリデーションを行う
     */
    public PatternName {
        Objects.requireNonNull(value, "パターン名はnullにできません");

        // 空白を除去した上でバリデーション
        if (value.isBlank()) {
            throw new IllegalArgumentException("パターン名は空にできません");
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "パターン名は" + MIN_LENGTH + "〜" + MAX_LENGTH + "文字で入力してください（現在: " + value.length() + "文字）"
            );
        }
    }
}
