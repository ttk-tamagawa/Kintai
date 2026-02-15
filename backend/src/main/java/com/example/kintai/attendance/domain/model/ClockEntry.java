package com.example.kintai.attendance.domain.model;

import java.util.Objects;

/**
 * 打刻エントリVO — 1回の打刻を表す不変の値オブジェクト
 *
 * <p>打刻の種類（出勤/退勤/休憩開始/休憩終了）、時刻、打刻元を保持する。
 * イミュータブルであり、一度記録されたら変更・削除されない（追記のみ）。
 * 打刻修正の場合はsource=CORRECTIONの新しいエントリが追加される。</p>
 *
 * @param type   打刻種別（null不可）
 * @param time   打刻時刻（null不可）
 * @param source 打刻元（null不可）
 */
public record ClockEntry(ClockType type, ClockTime time, ClockSource source) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public ClockEntry {
        Objects.requireNonNull(type, "打刻種別はnullにできません");
        Objects.requireNonNull(time, "打刻時刻はnullにできません");
        Objects.requireNonNull(source, "打刻元はnullにできません");
    }
}
