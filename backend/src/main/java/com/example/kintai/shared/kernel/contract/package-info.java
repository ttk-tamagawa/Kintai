/**
 * 共有カーネルのコントラクト — DDD の骨格となる基底クラス・マーカーインターフェース
 *
 * <p>このパッケージにはコンテキスト横断で使用する以下の型を配置する:
 * <ul>
 *   <li>{@link com.example.kintai.shared.kernel.contract.DomainEvent} — ドメインイベント基底クラス</li>
 *   <li>{@link com.example.kintai.shared.kernel.contract.AggregateRoot} — 集約ルート基底クラス</li>
 *   <li>{@link com.example.kintai.shared.kernel.contract.Command} — コマンド入力マーカーインターフェース</li>
 *   <li>{@link com.example.kintai.shared.kernel.contract.UseCase} — ユースケース実行インターフェース</li>
 * </ul>
 * </p>
 */
package com.example.kintai.shared.kernel.contract;
