package com.example.kintai.attendance.domain.model;

/**
 * 打刻種別 — 打刻エントリの種類を分類する列挙型
 *
 * <p>打刻ログ（ClockEntry）に記録される4種類の打刻タイプを定義する。
 * 休憩はBREAK_STARTとBREAK_ENDが必ず対になる必要がある（INV-ATT-005）。</p>
 */
public enum ClockType {

    /** 出勤打刻 — 勤務開始時に記録 */
    CLOCK_IN,

    /** 退勤打刻 — 勤務終了時に記録 */
    CLOCK_OUT,

    /** 休憩開始 — 休憩に入るときに記録 */
    BREAK_START,

    /** 休憩終了 — 休憩から戻るときに記録 */
    BREAK_END
}
