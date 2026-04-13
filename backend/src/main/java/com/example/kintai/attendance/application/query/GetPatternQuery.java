package com.example.kintai.attendance.application.query;

import com.example.kintai.shared.kernel.contract.Query;

import java.util.UUID;

/**
 * シフトパターン詳細取得クエリ（UC-SH-Q02）
 *
 * <p>シフトパターンの詳細を取得するクエリへの入力パラメータをまとめる。</p>
 *
 * @param patternId シフトパターンID
 */
public record GetPatternQuery(
        UUID patternId
) implements Query {
}
