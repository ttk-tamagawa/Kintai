package com.example.kintai.shared.kernel.contract;

/**
 * Command マーカーインターフェース — ユースケースへの入力を型レベルで識別する
 *
 * <p>すべてのコマンド（CUD 操作の入力パラメータ）はこのインターフェースを実装する。
 * メソッドは持たないマーカーインターフェースであり、以下の目的で使用する:
 * <ul>
 *   <li>{@link UseCase} のジェネリクス制約として、入力が Command であることを型安全に保証する</li>
 *   <li>IDE 検索で「プロジェクト内の全コマンド一覧」を即座に把握できるようにする</li>
 * </ul>
 * </p>
 *
 * <p>Command は record 型で実装し、ユースケースに必要な入力パラメータをまとめる。</p>
 *
 * <p>使用例:
 * <pre>{@code
 * public record ClockInCommand(
 *     EmployeeId employeeId,
 *     ClockTime clockTime,
 *     ShiftPatternId shiftPatternId
 * ) implements Command {}
 * }</pre>
 * </p>
 */
public interface Command {
}
