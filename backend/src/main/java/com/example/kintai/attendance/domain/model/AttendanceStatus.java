package com.example.kintai.attendance.domain.model;

/**
 * 勤怠ステータス — 勤怠記録の状態遷移を表す列挙型
 *
 * <p>状態遷移ルール:
 * <ul>
 *   <li>NOT_CLOCKED → CLOCKED_IN（出勤打刻）</li>
 *   <li>NOT_CLOCKED → CLOCKED_OUT（手動勤務登録）</li>
 *   <li>CLOCKED_IN → CLOCKED_OUT（退勤打刻）</li>
 *   <li>CLOCKED_IN → CLOCKED_IN（休憩開始/終了）</li>
 *   <li>CLOCKED_OUT → FINALIZED（本締め確定）</li>
 * </ul>
 * </p>
 */
public enum AttendanceStatus {

    /** 未打刻 — 勤務日の初期状態。出勤打刻待ち */
    NOT_CLOCKED,

    /** 出勤中 — 出勤打刻済み。休憩・退勤が可能 */
    CLOCKED_IN,

    /** 退勤済み — 退勤打刻済み。打刻修正・本締め待ち */
    CLOCKED_OUT,

    /** 確定済み — 月次本締め完了。以降一切変更不可 */
    FINALIZED
}
