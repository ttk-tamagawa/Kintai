package com.example.kintai.attendance.domain.model;

/**
 * 勤務種別 — 手動勤務登録時の出勤タイプを分類する列挙型
 *
 * <p>手動勤務登録（ManualAttendance）で使用される5種類の勤務タイプを定義する。
 * 打刻漏れ補完や出張・在宅勤務等の勤務実績を分類するために使用する。</p>
 */
public enum AttendanceType {

    /** 通常勤務 — オフィスでの通常の勤務 */
    NORMAL,

    /** 出張 — 外出先での業務 */
    BUSINESS_TRIP,

    /** 在宅勤務 — リモートワーク */
    REMOTE,

    /** 有給休暇 — 年次有給休暇の取得 */
    PAID_LEAVE,

    /** 欠勤 — 無届・事前承認済みの欠勤 */
    ABSENCE
}
