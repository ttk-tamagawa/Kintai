package com.example.kintai.attendance.infrastructure.projector;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 部門統計リフレッシャー — マテリアライズドビューを更新する
 *
 * <p>department_attendance_stats マテリアライズドビューを
 * REFRESH MATERIALIZED VIEW CONCURRENTLY で無停止更新する。
 * CONCURRENTLY キーワードにより、更新中も SELECT クエリが可能。</p>
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

    /** マテリアライズドビューのリフレッシュ SQL（CONCURRENTLY で無停止更新） */
    private static final String REFRESH_SQL =
            "REFRESH MATERIALIZED VIEW CONCURRENTLY department_attendance_stats";

    /** jOOQ DSLContext — ネイティブ SQL の実行に使用 */
    private final DSLContext dsl;

    public DepartmentStatsRefresher(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * 部門統計マテリアライズドビューをリフレッシュする
     *
     * <p>REFRESH MATERIALIZED VIEW CONCURRENTLY を実行して、
     * monthly_attendance_summaries の最新データを部門統計ビューに反映する。
     * CONCURRENTLY を使用するため、UNIQUE INDEX が必要（定義済み）。</p>
     *
     * <p>注意: マテリアライズドビューにデータが存在しない初回は
     * CONCURRENTLY が失敗する可能性がある。その場合はログ出力して処理を継続する。</p>
     */
    public void refresh() {
        try {
            log.debug("部門統計マテリアライズドビュー リフレッシュ開始");

            // CONCURRENTLY で無停止リフレッシュを実行する
            dsl.execute(REFRESH_SQL);

            log.debug("部門統計マテリアライズドビュー リフレッシュ完了");
        } catch (Exception e) {
            // 初回実行時（WITH NO DATA で作成済み）やデータ不足時のエラーをハンドリングする
            log.warn("部門統計マテリアライズドビュー リフレッシュ失敗: {}", e.getMessage());
        }
    }
}
