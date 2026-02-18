package com.example.kintai.attendance.infrastructure.projector;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 部門統計リフレッシャー — マテリアライズドビューを更新する
 *
 * <p>department_attendance_statsマテリアライズドビューを
 * REFRESH MATERIALIZED VIEW CONCURRENTLYで無停止更新する。
 * CONCURRENTLYキーワードにより、更新中もSELECTクエリが可能。</p>
 *
 * <p>呼び出し元:
 * <ul>
 *   <li>MonthlySummaryProjector — 月次サマリー更新後に自動実行</li>
 *   <li>バッチ処理 — 定期的なリフレッシュジョブとして呼び出し可能</li>
 * </ul>
 * </p>
 *
 * <p>設計書: 30_設計/データベース/勤怠記録.md の「department_attendance_stats」に対応</p>
 */
@Component
public class DepartmentStatsRefresher {

    private static final Logger log = LoggerFactory.getLogger(DepartmentStatsRefresher.class);

    /** マテリアライズドビューのリフレッシュSQL（CONCURRENTLYで無停止更新） */
    private static final String REFRESH_SQL =
            "REFRESH MATERIALIZED VIEW CONCURRENTLY department_attendance_stats";

    private final EntityManager entityManager;

    public DepartmentStatsRefresher(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 部門統計マテリアライズドビューをリフレッシュする
     *
     * <p>REFRESH MATERIALIZED VIEW CONCURRENTLYを実行して、
     * monthly_attendance_summariesの最新データを部門統計ビューに反映する。
     * CONCURRENTLYを使用するため、UNIQUE INDEXが必要（定義済み）。</p>
     *
     * <p>注意: マテリアライズドビューにデータが存在しない初回は
     * CONCURRENTLYが失敗する可能性がある。その場合はログ出力して処理を継続する。</p>
     */
    public void refresh() {
        try {
            log.debug("部門統計マテリアライズドビュー リフレッシュ開始");

            // CONCURRENTLYで無停止リフレッシュを実行する
            entityManager.createNativeQuery(REFRESH_SQL).executeUpdate();

            log.debug("部門統計マテリアライズドビュー リフレッシュ完了");
        } catch (Exception e) {
            // 初回実行時（WITH NO DATAで作成済み）やデータ不足時のエラーをハンドリングする
            log.warn("部門統計マテリアライズドビュー リフレッシュ失敗: {}", e.getMessage());
        }
    }
}
