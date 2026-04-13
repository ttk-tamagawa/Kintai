package com.example.kintai.attendance.application.query;

import com.example.kintai.shared.kernel.contract.Query;

/**
 * シフトパターン一覧取得クエリ（UC-SH-Q01）
 *
 * <p>シフトパターン一覧を取得するクエリへの入力パラメータをまとめる。</p>
 *
 * @param isActive true=有効のみ, false=無効のみ, null=全件
 */
public record GetPatternsQuery(
        Boolean isActive
) implements Query {
}
