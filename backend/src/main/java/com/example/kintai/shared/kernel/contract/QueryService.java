package com.example.kintai.shared.kernel.contract;

/**
 * QueryService インターフェース — 1クラス1クエリの実行契約を定義する
 *
 * <p>すべてのクエリ系サービス（R 操作）はこのインターフェースを実装する。
 * 型パラメータにより「入力は何か」「出力は何か」が型レベルで明示され、
 * IDE 検索で「プロジェクト内の全クエリサービス一覧」を即座に把握できる。</p>
 *
 * <p>型パラメータ:
 * <ul>
 *   <li>{@code Q} — クエリ型。{@link Query} を実装した record</li>
 *   <li>{@code R} — 戻り値型。DTOやサマリーなど、クエリの結果を表す</li>
 * </ul>
 * </p>
 *
 * <p>使用例:
 * <pre>{@code
 * @Service
 * public class GetTodayAttendanceQueryService implements QueryService<GetTodayAttendanceQuery, TodayAttendanceResult> {
 *     @Override
 *     public TodayAttendanceResult execute(GetTodayAttendanceQuery query) {
 *         // ... 読み取りモデルからデータを取得するロジック ...
 *     }
 * }
 * }</pre>
 * </p>
 *
 * @param <Q> クエリ型（{@link Query} の実装クラス）
 * @param <R> 戻り値型
 */
public interface QueryService<Q extends Query, R> {

    /**
     * クエリを実行する
     *
     * <p>クエリに含まれる検索条件をもとに、
     * 読み取りモデルからデータを取得して結果を返す。</p>
     *
     * @param query クエリへの入力パラメータ
     * @return クエリの実行結果
     */
    R execute(Q query);
}
