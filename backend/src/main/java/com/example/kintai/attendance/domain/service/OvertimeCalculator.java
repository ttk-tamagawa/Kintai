package com.example.kintai.attendance.domain.service;

import com.example.kintai.attendance.domain.model.OvertimeDuration;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 残業時間計算サービス — 勤務時間から残業時間の内訳を算出する
 *
 * <p>労働基準法に基づく割増率区分ごとに残業時間を算出する。
 * <ul>
 *   <li>通常残業（時間外）: ×1.25 — 所定労働時間を超えた分</li>
 *   <li>深夜勤務（22:00-5:00）: ×1.25 — 深夜帯の勤務時間</li>
 *   <li>時間外 + 深夜: ×1.50 — 残業かつ深夜帯</li>
 *   <li>休日勤務（法定休日）: ×1.35 — 日曜日の勤務</li>
 *   <li>休日 + 深夜: ×1.60 — 休日勤務かつ深夜帯</li>
 * </ul>
 * </p>
 *
 * <p>端数処理:
 * <ul>
 *   <li>日次計算: 1分単位（端数なし）— 労基法の全額払いの原則</li>
 *   <li>月次集計: 1時間単位に丸め（30分未満切り捨て、30分以上切り上げ）</li>
 * </ul>
 * </p>
 */
public class OvertimeCalculator {

    /**
     * 残業時間の内訳を算出する
     *
     * <p>計算手順:
     * <ol>
     *   <li>通常残業時間 = max(0, 実労働時間 - 所定労働時間)</li>
     *   <li>深夜時間帯の判定（LateNightDetectorに委譲）</li>
     *   <li>休日勤務の判定（法定休日 = 日曜日）</li>
     *   <li>合計残業時間の算出</li>
     * </ol>
     * </p>
     *
     * @param workDate         勤務日
     * @param netWorkMinutes   実労働時間（分）— 休憩除外済み
     * @param scheduledMinutes 所定労働時間（分）— シフトで定められた時間
     * @param lateNightMinutes 深夜勤務時間（分）— LateNightDetectorで算出済み
     * @return OvertimeDuration（残業時間の4区分内訳）
     */
    public OvertimeDuration calculate(
            LocalDate workDate,
            int netWorkMinutes,
            int scheduledMinutes,
            int lateNightMinutes
    ) {
        // 通常残業時間: 実労働時間が所定を超えた分（0以上）
        int regularOvertimeMinutes = Math.max(0, netWorkMinutes - scheduledMinutes);

        // 休日勤務の判定: 法定休日（日曜日）かどうか
        boolean isHoliday = (workDate.getDayOfWeek() == DayOfWeek.SUNDAY);

        // 休日勤務時間: 休日の場合は実労働時間全体が休日勤務
        int holidayMinutes = isHoliday ? netWorkMinutes : 0;

        // 合計残業時間の算出
        // 休日の場合: 休日勤務時間が全体の残業扱い（通常残業との重複を避ける）
        // 平日の場合: 通常残業 + 深夜加算
        int totalOvertimeMinutes;
        if (isHoliday) {
            // 休日: 全労働時間が残業扱い
            totalOvertimeMinutes = holidayMinutes;
        } else {
            // 平日: 通常残業時間が合計（深夜は別区分で割増率計算に使用）
            totalOvertimeMinutes = regularOvertimeMinutes;
        }

        return OvertimeDuration.of(
                regularOvertimeMinutes,
                lateNightMinutes,
                holidayMinutes,
                totalOvertimeMinutes
        );
    }

    /**
     * 月次集計用の端数処理を行う
     *
     * <p>月次集計では1時間単位に丸める（昭63.3.14基発150号）。
     * 30分未満は切り捨て、30分以上は切り上げ。</p>
     *
     * @param totalMinutes 月次合計の残業時間（分）
     * @return 1時間単位に丸めた残業時間（分）
     */
    public static int roundToHourForMonthly(int totalMinutes) {
        // 余りの分数を算出
        int remainder = totalMinutes % 60;

        if (remainder < 30) {
            // 30分未満: 切り捨て
            return totalMinutes - remainder;
        } else {
            // 30分以上: 切り上げ
            return totalMinutes - remainder + 60;
        }
    }
}
