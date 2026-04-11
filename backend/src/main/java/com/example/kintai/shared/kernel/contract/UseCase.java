package com.example.kintai.shared.kernel.contract;

/**
 * UseCase インターフェース — 1クラス1ユースケースの実行契約を定義する
 *
 * <p>すべてのコマンド系ユースケース（CUD 操作）はこのインターフェースを実装する。
 * 型パラメータにより「入力は何か」「出力は何か」が型レベルで明示され、
 * IDE 検索で「プロジェクト内の全ユースケース一覧」を即座に把握できる。</p>
 *
 * <p>型パラメータ:
 * <ul>
 *   <li>{@code C} — コマンド型。{@link Command} を実装した record</li>
 *   <li>{@code R} — 戻り値型。集約IDや Void など、ユースケースの結果を表す</li>
 * </ul>
 * </p>
 *
 * <p>使用例:
 * <pre>{@code
 * @Service
 * public class ClockInUseCase implements UseCase<ClockInCommand, AttendanceRecordId> {
 *     @Override
 *     public AttendanceRecordId execute(ClockInCommand command) {
 *         // ... ユースケースの実行ロジック ...
 *     }
 * }
 * }</pre>
 * </p>
 *
 * @param <C> コマンド型（{@link Command} の実装クラス）
 * @param <R> 戻り値型
 */
public interface UseCase<C extends Command, R> {

    /**
     * ユースケースを実行する
     *
     * <p>コマンドに含まれる入力パラメータをもとに、
     * ビジネスロジックを実行して結果を返す。</p>
     *
     * @param command ユースケースへの入力パラメータ
     * @return ユースケースの実行結果
     */
    R execute(C command);
}
