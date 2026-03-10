package com.example.kintai.attendance.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 打刻時刻VO — 打刻が行われた瞬間を表す値オブジェクト
 *
 * <p>Instantをラップすることで、打刻時刻としての意味を型で表現する。
 * タイムゾーン非依存のUTC瞬間値を保持し、表示時にタイムゾーン変換を行う。</p>
 *
 * @param value 打刻時刻のInstant値（null不可）
 */
public record ClockTime(Instant value) {

    /**
     * コンパクトコンストラクタ — null値を拒否する
     */
    public ClockTime {
        Objects.requireNonNull(value, "打刻時刻はnullにできません");
    }
}
