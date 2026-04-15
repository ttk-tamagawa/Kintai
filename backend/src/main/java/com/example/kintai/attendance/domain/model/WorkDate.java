package com.example.kintai.attendance.domain.model;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 勤務日VO — 勤怠記録の対象日を表す値オブジェクト
 *
 * <p>LocalDateをラップし、勤務日としてのドメイン知識を持つ。
 * 夜勤の場合、勤務開始日が「勤務日」となる（夜勤ルール）。</p>
 *
 * @param value 勤務日のLocalDate値（null不可）
 */
public record WorkDate(LocalDate value) {

    /** タイムゾーン: Asia/Tokyo（打刻時刻から勤務日を算出する際に使用） */
    private static final ZoneId ZONE_TOKYO = ZoneId.of("Asia/Tokyo");

    /**
     * コンパクトコンストラクタ — null値を拒否する
     */
    public WorkDate {
        Objects.requireNonNull(value, "勤務日はnullにできません");
    }

    /**
     * 打刻時刻からAsia/Tokyoタイムゾーンで勤務日を算出する
     *
     * <p>「打刻時刻がどの勤務日に属するか」はドメインルールであり、
     * タイムゾーン変換を伴うビジネス判断のため、値オブジェクト自身が持つ。</p>
     *
     * @param clockTime 打刻時刻
     * @return 勤務日（Asia/Tokyo基準）
     */
    public static WorkDate from(ClockTime clockTime) {
        Objects.requireNonNull(clockTime, "打刻時刻はnullにできません");
        return new WorkDate(clockTime.value().atZone(ZONE_TOKYO).toLocalDate());
    }

    /**
     * 夜勤日かどうかを判定する
     *
     * <p>この勤務日が夜勤シフトの開始日となりうるかを判定する。
     * 夜勤の場合、勤務が翌日にまたがるため、勤務時間計算や
     * 深夜割増の判定で考慮が必要になる。</p>
     *
     * <p>実際の夜勤判定はシフトパターンとの組み合わせで行うため、
     * ここでは日付としての夜勤可能性（全日が夜勤開始日になりうる）を返す。</p>
     *
     * @return 夜勤の開始日となりうる場合true
     */
    public boolean isNightShiftDate() {
        // すべての日付は夜勤シフトの開始日になりうる
        // 実際の夜勤判定はShiftPatternのisOvernightフラグで行う
        return true;
    }
}
