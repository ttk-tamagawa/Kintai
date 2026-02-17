package com.example.kintai.attendance.domain.model.shift;

/**
 * スケジュールステータス — 週次スケジュールの状態を表す列挙型
 *
 * <p>状態遷移ルール:
 * <ul>
 *   <li>DRAFT → PUBLISHED（公開）</li>
 *   <li>PUBLISHED → DRAFT（変更時にDRAFTへ戻る）</li>
 * </ul>
 * </p>
 */
public enum ScheduleStatus {

    /** 下書き — 編集中。公開前の状態 */
    DRAFT,

    /** 公開済み — 従業員に通知済み。変更するとDRAFTに戻る */
    PUBLISHED
}
