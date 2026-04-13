package com.example.kintai.shared.kernel.contract;

/**
 * Query マーカーインターフェース — クエリサービスへの入力を型レベルで識別する
 *
 * <p>すべてのクエリ（R 操作の入力パラメータ）はこのインターフェースを実装する。
 * メソッドは持たないマーカーインターフェースであり、以下の目的で使用する:
 * <ul>
 *   <li>{@link QueryService} のジェネリクス制約として、入力が Query であることを型安全に保証する</li>
 *   <li>IDE 検索で「プロジェクト内の全クエリ一覧」を即座に把握できるようにする</li>
 * </ul>
 * </p>
 *
 * <p>Query は record 型で実装し、クエリに必要な検索条件をまとめる。</p>
 *
 * <p>使用例:
 * <pre>{@code
 * public record GetTodayAttendanceQuery(
 *     EmployeeId employeeId,
 *     WorkDate workDate
 * ) implements Query {}
 * }</pre>
 * </p>
 */
public interface Query {
}
