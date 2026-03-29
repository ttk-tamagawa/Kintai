package com.example.kintai.attendance.domain.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 深夜時間帯判定サービス — 22:00-5:00の深夜勤務時間を算出する
 *
 * <p>労働基準法に基づき、22:00〜翌5:00を深夜時間帯として判定する。
 * 日跨ぎの勤務（夜勤シフト等）にも対応する。</p>
 *
 * <p>割増率:
 * <ul>
 *   <li>深夜勤務: ×1.25</li>
 *   <li>時間外 + 深夜: ×1.50（1.25 + 0.25）</li>
 *   <li>休日 + 深夜: ×1.60（1.35 + 0.25）</li>
 * </ul>
 * </p>
 *
 * <p>計算精度: 分単位（秒以下は切り捨て）</p>
 */
@Service
public class LateNightDetector {

    /** 深夜時間帯の開始: 22:00 */
    private static final LocalTime LATE_NIGHT_START = LocalTime.of(22, 0);

    /** 深夜時間帯の終了: 翌5:00 */
    private static final LocalTime LATE_NIGHT_END = LocalTime.of(5, 0);

    /**
     * 勤務時間のうち深夜時間帯（22:00-5:00）に該当する分数を算出する
     *
     * <p>計算手順:
     * <ol>
     *   <li>勤務開始・終了をLocalDateTimeに変換する</li>
     *   <li>勤務時間と深夜帯の重なりを計算する</li>
     *   <li>日跨ぎの場合は前日22:00-24:00と当日0:00-5:00を分けて計算する</li>
     * </ol>
     * </p>
     *
     * @param workDate     勤務日
     * @param clockInTime  出勤時刻（LocalDateTime、日本時間）
     * @param clockOutTime 退勤時刻（LocalDateTime、日本時間）
     * @return 深夜勤務時間（分）
     */
    public int calculateLateNightMinutes(
            LocalDate workDate,
            LocalDateTime clockInTime,
            LocalDateTime clockOutTime
    ) {
        Objects.requireNonNull(workDate, "勤務日はnullにできません");
        Objects.requireNonNull(clockInTime, "出勤時刻はnullにできません");
        Objects.requireNonNull(clockOutTime, "退勤時刻はnullにできません");

        int totalLateNightMinutes = 0;

        // 勤務開始日から退勤日まで、日ごとに深夜帯との重なりを計算する
        LocalDate currentDate = clockInTime.toLocalDate();
        LocalDate endDate = clockOutTime.toLocalDate();

        while (!currentDate.isAfter(endDate)) {
            // 当日の深夜帯: 0:00-5:00
            LocalDateTime morningStart = currentDate.atTime(LocalTime.MIDNIGHT);
            LocalDateTime morningEnd = currentDate.atTime(LATE_NIGHT_END);
            totalLateNightMinutes += calculateOverlapMinutes(
                    clockInTime, clockOutTime, morningStart, morningEnd
            );

            // 当日の深夜帯: 22:00-24:00（翌日の0:00）
            LocalDateTime eveningStart = currentDate.atTime(LATE_NIGHT_START);
            LocalDateTime eveningEnd = currentDate.plusDays(1).atTime(LocalTime.MIDNIGHT);
            totalLateNightMinutes += calculateOverlapMinutes(
                    clockInTime, clockOutTime, eveningStart, eveningEnd
            );

            currentDate = currentDate.plusDays(1);
        }

        return totalLateNightMinutes;
    }

    /**
     * 2つの時間範囲の重なり（分）を計算する
     *
     * <p>勤務時間帯と深夜時間帯が重なる部分の分数を返す。
     * 重なりがない場合は0を返す。</p>
     *
     * @param workStart  勤務開始時刻
     * @param workEnd    勤務終了時刻
     * @param rangeStart 深夜帯の開始時刻
     * @param rangeEnd   深夜帯の終了時刻
     * @return 重なり時間（分）。重なりがなければ0
     */
    private int calculateOverlapMinutes(
            LocalDateTime workStart,
            LocalDateTime workEnd,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd
    ) {
        // 重なりの開始: 勤務開始と深夜帯開始の遅い方
        LocalDateTime overlapStart = workStart.isAfter(rangeStart) ? workStart : rangeStart;

        // 重なりの終了: 勤務終了と深夜帯終了の早い方
        LocalDateTime overlapEnd = workEnd.isBefore(rangeEnd) ? workEnd : rangeEnd;

        // 重なりがない場合は0を返す
        if (!overlapStart.isBefore(overlapEnd)) {
            return 0;
        }

        // 重なり時間を分単位で返す
        return (int) ChronoUnit.MINUTES.between(overlapStart, overlapEnd);
    }
}
