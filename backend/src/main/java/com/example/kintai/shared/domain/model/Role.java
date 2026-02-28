package com.example.kintai.shared.domain.model;

/**
 * ユーザーロール定義
 *
 * <p>システム全体で使用する4つのロールを定義する。
 * ロールは階層構造を持たず、従業員に複数割り当て可能。</p>
 *
 * <ul>
 *   <li>EMPLOYEE — 一般社員（打刻・自身の勤怠閲覧）</li>
 *   <li>MANAGER — 管理者（部署の勤怠閲覧・シフト割当）</li>
 *   <li>HR — 人事（シフトパターン管理・全社勤怠閲覧）</li>
 *   <li>ADMIN — システム管理者（手動登録・確定・全権限）</li>
 * </ul>
 */
public enum Role {
    EMPLOYEE,
    MANAGER,
    HR,
    ADMIN
}
