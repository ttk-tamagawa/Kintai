package com.example.kintai.attendance.domain.model;

/**
 * 打刻元 — 打刻がどの経路で行われたかを示す列挙型
 *
 * <p>打刻の出所を追跡することで、監査ログや打刻修正の判別に活用する。
 * 従業員が直接操作するのはWEB/MOBILEのみ。
 * MANUAL/CORRECTIONはシステムが承認済み申請に基づいて自動設定する。</p>
 */
public enum ClockSource {

    /** Web画面からの打刻 */
    WEB,

    /** モバイルアプリからの打刻 */
    MOBILE,

    /** 手動勤務登録（承認済み申請経由） */
    MANUAL,

    /** 打刻修正（承認済み修正申請経由） */
    CORRECTION
}
