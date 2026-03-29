package com.example.kintai.attendance.domain.service;

import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.model.ClockEntry;
import com.example.kintai.attendance.domain.model.ClockType;
import com.example.kintai.attendance.domain.model.OvertimeDuration;
import com.example.kintai.attendance.domain.model.WorkDuration;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * 勤務時間計算サービス — 3つの勤務パターンに対応する勤務時間計算の統合サービス
 *
 * <p>退勤打刻後や打刻修正後に呼び出され、勤務時間と残業時間を計算する。
 * 3つの勤務パターン（固定時間制/シフト制/フレックスタイム制）に対応する。</p>
 *
 * <p>勤務パターン別の計算ルール:
 * <ul>
 *   <li>固定時間制: 所定労働時間 = 480分（8時間）固定</li>
 *   <li>シフト制: 所定労働時間 = シフトパターンから取得（可変）</li>
 *   <li>フレックスタイム制: 日次では残業計算しない（月次精算）</li>
 * </ul>
 * </p>
 *
 * <p>計算精度: 分単位（秒以下は切り捨て）</p>
 */
@Service
public class WorkDurationCalculator {

    /** 固定時間制の所定労働時間: 480分（8時間） */
    private static final int FIXED_SCHEDULED_MINUTES = 480;

    /** タイムゾーン: Asia/Tokyo（深夜判定やLocalDateTime変換に使用） */
    private static final ZoneId ZONE_TOKYO = ZoneId.of("Asia/Tokyo");

    /** 休憩時間計算サービス */
    private final BreakTimeCalculator breakTimeCalculator;

    /** 残業時間計算サービス */
    private final OvertimeCalculator overtimeCalculator;

    /** 深夜時間帯判定サービス */
    private final LateNightDetector lateNightDetector;

    /**
     * コンストラクタ
     *
     * @param breakTimeCalculator 休憩時間計算サービス
     * @param overtimeCalculator  残業時間計算サービス
     * @param lateNightDetector   深夜時間帯判定サービス
     */
    public WorkDurationCalculator(
            BreakTimeCalculator breakTimeCalculator,
            OvertimeCalculator overtimeCalculator,
            LateNightDetector lateNightDetector
    ) {
        this.breakTimeCalculator = Objects.requireNonNull(breakTimeCalculator);
        this.overtimeCalculator = Objects.requireNonNull(overtimeCalculator);
        this.lateNightDetector = Objects.requireNonNull(lateNightDetector);
    }

    /**
     * 固定時間制の勤務時間を計算する
     *
     * <p>所定労働時間は480分（8時間）固定。
     * 超過分が通常残業となる。</p>
     *
     * @param record 勤怠記録（退勤打刻済みであること）
     * @return 計算結果（WorkDuration + OvertimeDuration）
     */
    public CalculationResult calculateForFixed(AttendanceRecord record) {
        Objects.requireNonNull(record, "勤怠記録はnullにできません");

        return calculateInternal(record, FIXED_SCHEDULED_MINUTES);
    }

    /**
     * シフト制の勤務時間を計算する
     *
     * <p>所定労働時間はシフトパターンから取得する（可変）。
     * 超過分が通常残業となる。</p>
     *
     * @param record           勤怠記録（退勤打刻済みであること）
     * @param scheduledMinutes シフトパターンの所定労働時間（分）
     * @return 計算結果（WorkDuration + OvertimeDuration）
     */
    public CalculationResult calculateForShift(AttendanceRecord record, int scheduledMinutes) {
        Objects.requireNonNull(record, "勤怠記録はnullにできません");

        return calculateInternal(record, scheduledMinutes);
    }

    /**
     * フレックスタイム制の勤務時間を計算する
     *
     * <p>フレックスは月次精算のため、日次では残業計算を行わない。
     * 所定労働時間は0として扱い、残業時間もゼロで返す。
     * 月次精算は別途MonthlyFlexSettlement（将来実装）で行う。</p>
     *
     * <p>月次精算の計算式:
     * 月の所定時間 = floor(暦日数 × 8時間 × 60分 / 7)
     * 月の残業時間 = max(0, 月の実労働合計 - 月の所定時間)</p>
     *
     * @param record 勤怠記録（退勤打刻済みであること）
     * @return 計算結果（WorkDuration + OvertimeDuration）— 残業はゼロ
     */
    public CalculationResult calculateForFlex(AttendanceRecord record) {
        Objects.requireNonNull(record, "勤怠記録はnullにできません");

        List<ClockEntry> entries = record.getClockEntries();

        // 出勤・退勤時刻を取得する
        Instant clockInTime = findClockTime(entries, ClockType.CLOCK_IN);
        Instant clockOutTime = findClockTime(entries, ClockType.CLOCK_OUT);

        // 実勤務時間（分）= 出勤〜退勤の差分
        int actualMinutes = (int) Duration.between(clockInTime, clockOutTime).toMinutes();

        // 休憩時間（分）を計算する
        int breakMinutes = breakTimeCalculator.calculateTotalBreakMinutes(entries);

        // 実労働時間（分）= 実勤務時間 - 休憩時間
        int netWorkMinutes = actualMinutes - breakMinutes;

        // フレックスは日次で残業計算しない（所定0、残業ゼロ）
        WorkDuration workDuration = WorkDuration.of(0, actualMinutes, breakMinutes, netWorkMinutes);

        // 深夜勤務時間だけは日次で計算する（割増率に必要）
        LocalDate workDate = record.getWorkDate().value();
        LocalDateTime clockInLocal = LocalDateTime.ofInstant(clockInTime, ZONE_TOKYO);
        LocalDateTime clockOutLocal = LocalDateTime.ofInstant(clockOutTime, ZONE_TOKYO);
        int lateNightMinutes = lateNightDetector.calculateLateNightMinutes(
                workDate, clockInLocal, clockOutLocal
        );

        // 残業はゼロだが、深夜時間は記録する
        OvertimeDuration overtimeDuration = OvertimeDuration.of(0, lateNightMinutes, 0, 0);

        return new CalculationResult(workDuration, overtimeDuration);
    }

    /**
     * 内部共通の計算処理 — 固定時間制とシフト制で共有する
     *
     * <p>計算手順:
     * <ol>
     *   <li>打刻エントリから出勤・退勤時刻を取得する</li>
     *   <li>実勤務時間（出勤〜退勤）を算出する</li>
     *   <li>休憩時間を算出する（BreakTimeCalculatorに委譲）</li>
     *   <li>実労働時間を算出する（実勤務 - 休憩）</li>
     *   <li>深夜勤務時間を算出する（LateNightDetectorに委譲）</li>
     *   <li>残業時間を算出する（OvertimeCalculatorに委譲）</li>
     * </ol>
     * </p>
     *
     * @param record           勤怠記録
     * @param scheduledMinutes 所定労働時間（分）
     * @return 計算結果
     */
    private CalculationResult calculateInternal(AttendanceRecord record, int scheduledMinutes) {
        List<ClockEntry> entries = record.getClockEntries();

        // 出勤・退勤時刻を取得する
        Instant clockInTime = findClockTime(entries, ClockType.CLOCK_IN);
        Instant clockOutTime = findClockTime(entries, ClockType.CLOCK_OUT);

        // 実勤務時間（分）= 出勤〜退勤の差分
        int actualMinutes = (int) Duration.between(clockInTime, clockOutTime).toMinutes();

        // 休憩時間（分）を計算する
        int breakMinutes = breakTimeCalculator.calculateTotalBreakMinutes(entries);

        // 実労働時間（分）= 実勤務時間 - 休憩時間
        int netWorkMinutes = actualMinutes - breakMinutes;

        // WorkDuration（勤務時間の4内訳）を生成する
        WorkDuration workDuration = WorkDuration.of(
                scheduledMinutes, actualMinutes, breakMinutes, netWorkMinutes
        );

        // 深夜勤務時間を算出する（Instant→LocalDateTimeに変換）
        LocalDate workDate = record.getWorkDate().value();
        LocalDateTime clockInLocal = LocalDateTime.ofInstant(clockInTime, ZONE_TOKYO);
        LocalDateTime clockOutLocal = LocalDateTime.ofInstant(clockOutTime, ZONE_TOKYO);
        int lateNightMinutes = lateNightDetector.calculateLateNightMinutes(
                workDate, clockInLocal, clockOutLocal
        );

        // 残業時間の内訳を算出する
        OvertimeDuration overtimeDuration = overtimeCalculator.calculate(
                workDate, netWorkMinutes, scheduledMinutes, lateNightMinutes
        );

        return new CalculationResult(workDuration, overtimeDuration);
    }

    /**
     * 打刻エントリから指定種別の最新の打刻時刻を取得する
     *
     * <p>打刻修正（CORRECTION）がある場合は、最後のエントリが有効な時刻となる。
     * 修正エントリはリストの末尾に追加されるため、末尾から検索する。</p>
     *
     * @param entries 打刻エントリ一覧
     * @param type    取得する打刻種別
     * @return 最新の打刻時刻
     * @throws IllegalStateException 指定種別の打刻が見つからない場合
     */
    private Instant findClockTime(List<ClockEntry> entries, ClockType type) {
        // 末尾から検索して最新の打刻を取得する（修正エントリ対応）
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).type() == type) {
                return entries.get(i).time().value();
            }
        }
        throw new IllegalStateException(type + "の打刻が見つかりません");
    }

    /**
     * 計算結果 — WorkDurationとOvertimeDurationをセットで返す
     *
     * @param workDuration     勤務時間の内訳
     * @param overtimeDuration 残業時間の内訳
     */
    public record CalculationResult(
            WorkDuration workDuration,
            OvertimeDuration overtimeDuration
    ) {
        public CalculationResult {
            Objects.requireNonNull(workDuration, "勤務時間はnullにできません");
            Objects.requireNonNull(overtimeDuration, "残業時間はnullにできません");
        }
    }
}
