package com.example.kintai.attendance.domain.service;

import com.example.kintai.attendance.domain.model.*;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkDurationCalculator ドメインサービスのユニットテスト
 *
 * <p>3つの勤務パターン（固定時間制/シフト制/フレックスタイム制）に対応した
 * 勤務時間計算の正確性を検証する。依存サービス（BreakTimeCalculator,
 * OvertimeCalculator, LateNightDetector）は実際のクラスを使用する純粋なユニットテスト。</p>
 */
@DisplayName("WorkDurationCalculator: 勤務時間計算サービス")
class WorkDurationCalculatorTest {

    /** テスト対象の勤務時間計算サービス */
    private WorkDurationCalculator calculator;

    /** タイムゾーン: Asia/Tokyo */
    private static final ZoneId ZONE_TOKYO = ZoneId.of("Asia/Tokyo");

    // ========================
    // セットアップ
    // ========================

    @BeforeEach
    void setUp() {
        // 実際のドメインサービスを使用する（モック不要）
        BreakTimeCalculator breakTimeCalculator = new BreakTimeCalculator();
        OvertimeCalculator overtimeCalculator = new OvertimeCalculator();
        LateNightDetector lateNightDetector = new LateNightDetector();

        calculator = new WorkDurationCalculator(
                breakTimeCalculator,
                overtimeCalculator,
                lateNightDetector
        );
    }

    // ========================
    // ヘルパーメソッド
    // ========================

    /**
     * 日本時間のLocalDateTimeからInstantに変換する
     *
     * @param ldt 日本時間のLocalDateTime
     * @return UTC Instant
     */
    private static Instant toInstant(LocalDateTime ldt) {
        return ldt.atZone(ZONE_TOKYO).toInstant();
    }

    /**
     * 出勤エントリを生成する
     *
     * @param ldt 日本時間の出勤時刻
     * @return 出勤ClockEntry
     */
    private static ClockEntry clockIn(LocalDateTime ldt) {
        return new ClockEntry(ClockType.CLOCK_IN, new ClockTime(toInstant(ldt)), ClockSource.WEB);
    }

    /**
     * 退勤エントリを生成する
     *
     * @param ldt 日本時間の退勤時刻
     * @return 退勤ClockEntry
     */
    private static ClockEntry clockOut(LocalDateTime ldt) {
        return new ClockEntry(ClockType.CLOCK_OUT, new ClockTime(toInstant(ldt)), ClockSource.WEB);
    }

    /**
     * 休憩開始エントリを生成する
     *
     * @param ldt 日本時間の休憩開始時刻
     * @return 休憩開始ClockEntry
     */
    private static ClockEntry breakStart(LocalDateTime ldt) {
        return new ClockEntry(ClockType.BREAK_START, new ClockTime(toInstant(ldt)), ClockSource.WEB);
    }

    /**
     * 休憩終了エントリを生成する
     *
     * @param ldt 日本時間の休憩終了時刻
     * @return 休憩終了ClockEntry
     */
    private static ClockEntry breakEnd(LocalDateTime ldt) {
        return new ClockEntry(ClockType.BREAK_END, new ClockTime(toInstant(ldt)), ClockSource.WEB);
    }

    /**
     * 打刻修正エントリを生成する（CORRECTION source）
     *
     * @param type 修正対象の打刻種別
     * @param ldt  修正後の日本時間
     * @return 修正ClockEntry
     */
    private static ClockEntry correctionEntry(ClockType type, LocalDateTime ldt) {
        return new ClockEntry(type, new ClockTime(toInstant(ldt)), ClockSource.CORRECTION);
    }

    /**
     * テスト用のAttendanceRecordを生成する（reconstruct経由）
     *
     * <p>打刻エントリをそのまま渡してCLOCKED_OUT状態のレコードを復元する。
     * 勤務時間計算サービスのテストでは、退勤打刻済み状態のレコードが必要。</p>
     *
     * @param workDate     勤務日
     * @param clockEntries 打刻エントリ一覧
     * @return テスト用AttendanceRecord
     */
    private static AttendanceRecord createRecord(LocalDate workDate, List<ClockEntry> clockEntries) {
        Instant now = Instant.now();
        return AttendanceRecord.reconstruct(
                AttendanceRecordId.generate(),
                EmployeeId.generate(),
                new WorkDate(workDate),
                ShiftPatternId.generate(),
                AttendanceStatus.CLOCKED_OUT,
                clockEntries,
                WorkDuration.zero(),
                OvertimeDuration.zero(),
                0,
                now,
                now
        );
    }

    // ========================
    // 固定時間制のテスト
    // ========================

    @Nested
    @DisplayName("固定時間制（calculateForFixed）")
    class FixedWorkTypeTest {

        @Test
        @DisplayName("標準勤務: 9:00-18:00（休憩1時間）で実労働8時間、残業0分")
        void standardWorkDay_noOvertime() {
            // 準備: 9:00出勤、12:00-13:00休憩、18:00退勤（平日）
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 9, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 23, 12, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 18, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: WorkDuration
            WorkDuration wd = result.workDuration();
            assertEquals(480, wd.scheduledMinutes(), "所定勤務時間は480分（8時間）");
            assertEquals(540, wd.actualMinutes(), "実勤務時間は540分（9時間 = 9:00-18:00）");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(480, wd.netWorkMinutes(), "実労働時間は480分（540-60）");

            // 検証: OvertimeDuration
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(0, ot.regularOvertimeMinutes(), "通常残業は0分（所定丁度）");
            assertEquals(0, ot.lateNightMinutes(), "深夜勤務は0分");
            assertEquals(0, ot.holidayMinutes(), "休日勤務は0分");
            assertEquals(0, ot.totalOvertimeMinutes(), "合計残業は0分");
        }

        @Test
        @DisplayName("残業あり: 9:00-20:00（休憩1時間）で実労働10時間、残業120分")
        void overtimeWorkDay() {
            // 準備: 9:00出勤、12:00-13:00休憩、20:00退勤（平日）
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 9, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 23, 12, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 20, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: WorkDuration
            WorkDuration wd = result.workDuration();
            assertEquals(480, wd.scheduledMinutes(), "所定勤務時間は480分");
            assertEquals(660, wd.actualMinutes(), "実勤務時間は660分（11時間 = 9:00-20:00）");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(600, wd.netWorkMinutes(), "実労働時間は600分（660-60）");

            // 検証: OvertimeDuration — 実労働600分 - 所定480分 = 残業120分
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(120, ot.regularOvertimeMinutes(), "通常残業は120分（600-480）");
            assertEquals(0, ot.lateNightMinutes(), "深夜勤務は0分（20:00退勤で深夜帯に入らない）");
            assertEquals(0, ot.holidayMinutes(), "休日勤務は0分");
            assertEquals(120, ot.totalOvertimeMinutes(), "合計残業は120分");
        }

        @Test
        @DisplayName("所定労働時間ちょうど超過1分: 残業1分が計上される")
        void justOverScheduled_oneMinuteOvertime() {
            // 準備: 9:00出勤、12:00-13:00休憩、18:01退勤（所定を1分超過）
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 9, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 23, 12, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 18, 1))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: 実労働481分、残業1分
            WorkDuration wd = result.workDuration();
            assertEquals(541, wd.actualMinutes(), "実勤務時間は541分（9:00-18:01）");
            assertEquals(481, wd.netWorkMinutes(), "実労働時間は481分（541-60）");

            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(1, ot.regularOvertimeMinutes(), "通常残業は1分（481-480）");
            assertEquals(1, ot.totalOvertimeMinutes(), "合計残業は1分");
        }

        @Test
        @DisplayName("深夜残業: 9:00-23:00（休憩1時間）で深夜60分を含む")
        void lateNightOvertime() {
            // 準備: 9:00出勤、12:00-13:00休憩、23:00退勤（深夜帯22:00-23:00 = 60分）
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 9, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 23, 12, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 23, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: WorkDuration
            WorkDuration wd = result.workDuration();
            assertEquals(840, wd.actualMinutes(), "実勤務時間は840分（14時間 = 9:00-23:00）");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(780, wd.netWorkMinutes(), "実労働時間は780分（840-60）");

            // 検証: OvertimeDuration — 残業300分、深夜60分
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(300, ot.regularOvertimeMinutes(), "通常残業は300分（780-480）");
            assertEquals(60, ot.lateNightMinutes(), "深夜勤務は60分（22:00-23:00）");
            assertEquals(0, ot.holidayMinutes(), "休日勤務は0分（平日）");
            assertEquals(300, ot.totalOvertimeMinutes(), "合計残業は300分");
        }

        @Test
        @DisplayName("複数回休憩: 午前休憩15分 + 昼休憩60分 + 午後休憩15分 = 合計90分")
        void multipleBreakPeriods() {
            // 準備: 3回の休憩を含む勤務
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 8, 0)),
                    // 午前休憩: 10:00-10:15（15分）
                    breakStart(LocalDateTime.of(2026, 2, 23, 10, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 10, 15)),
                    // 昼休憩: 12:00-13:00（60分）
                    breakStart(LocalDateTime.of(2026, 2, 23, 12, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    // 午後休憩: 15:00-15:15（15分）
                    breakStart(LocalDateTime.of(2026, 2, 23, 15, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 15, 15)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 18, 30))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: 実勤務630分（8:00-18:30）、休憩90分、実労働540分
            WorkDuration wd = result.workDuration();
            assertEquals(630, wd.actualMinutes(), "実勤務時間は630分（10.5時間）");
            assertEquals(90, wd.breakMinutes(), "休憩時間は90分（15+60+15）");
            assertEquals(540, wd.netWorkMinutes(), "実労働時間は540分（630-90）");

            // 検証: 残業60分（540-480）
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(60, ot.regularOvertimeMinutes(), "通常残業は60分（540-480）");
        }

        @Test
        @DisplayName("休憩なし: 9:00-17:00で実労働480分、残業0分")
        void noBreak_exactScheduled() {
            // 準備: 休憩なしで8時間勤務
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 9, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 17, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: 実勤務=実労働=480分、休憩0分
            WorkDuration wd = result.workDuration();
            assertEquals(480, wd.actualMinutes(), "実勤務時間は480分");
            assertEquals(0, wd.breakMinutes(), "休憩時間は0分");
            assertEquals(480, wd.netWorkMinutes(), "実労働時間は480分");

            // 検証: 残業0分
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(0, ot.regularOvertimeMinutes(), "通常残業は0分");
            assertEquals(0, ot.totalOvertimeMinutes(), "合計残業は0分");
        }

        @Test
        @DisplayName("日曜日の勤務: 休日勤務として全労働時間が休日勤務扱い")
        void sundayWork_holidayOvertime() {
            // 準備: 日曜日の勤務（法定休日）
            LocalDate workDate = LocalDate.of(2026, 2, 22); // 日曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 22, 9, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 22, 12, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 22, 13, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 22, 18, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: WorkDuration — 通常通り計算
            WorkDuration wd = result.workDuration();
            assertEquals(480, wd.scheduledMinutes(), "所定勤務時間は480分");
            assertEquals(540, wd.actualMinutes(), "実勤務時間は540分");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(480, wd.netWorkMinutes(), "実労働時間は480分");

            // 検証: OvertimeDuration — 休日勤務として全労働時間が残業扱い
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(0, ot.regularOvertimeMinutes(), "通常残業は0分（所定ちょうど）");
            assertEquals(480, ot.holidayMinutes(), "休日勤務は480分（全労働時間）");
            assertEquals(480, ot.totalOvertimeMinutes(), "合計残業は480分（休日は全労働時間）");
        }

        @Test
        @DisplayName("深夜帯を跨ぐ夜勤: 22:00-翌5:00の深夜帯を正しく計算")
        void nightShift_crossesMidnight_lateNight() {
            // 準備: 21:00出勤、翌6:00退勤（夜勤、深夜帯 22:00-5:00 = 7時間 = 420分）
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 21, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 24, 6, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: WorkDuration
            WorkDuration wd = result.workDuration();
            assertEquals(480, wd.scheduledMinutes(), "所定勤務時間は480分");
            assertEquals(540, wd.actualMinutes(), "実勤務時間は540分（9時間 = 21:00-翌6:00）");
            assertEquals(0, wd.breakMinutes(), "休憩時間は0分");
            assertEquals(540, wd.netWorkMinutes(), "実労働時間は540分");

            // 検証: OvertimeDuration — 深夜帯 22:00-翌5:00 = 420分
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(60, ot.regularOvertimeMinutes(), "通常残業は60分（540-480）");
            assertEquals(420, ot.lateNightMinutes(), "深夜勤務は420分（22:00-翌5:00）");
            assertEquals(60, ot.totalOvertimeMinutes(), "合計残業は60分");
        }

        @Test
        @DisplayName("打刻修正: 退勤時刻修正後の最新打刻で計算される")
        void clockCorrection_usesLatestEntry() {
            // 準備: 退勤時刻を18:00→19:00に修正
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = new ArrayList<>(List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 9, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 23, 12, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    // 元の退勤: 18:00
                    clockOut(LocalDateTime.of(2026, 2, 23, 18, 0)),
                    // 修正: 退勤を19:00に修正
                    correctionEntry(ClockType.CLOCK_OUT, LocalDateTime.of(2026, 2, 23, 19, 0))
            ));
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: 修正後の19:00退勤で計算される
            WorkDuration wd = result.workDuration();
            assertEquals(600, wd.actualMinutes(), "実勤務時間は600分（10時間 = 9:00-19:00）");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(540, wd.netWorkMinutes(), "実労働時間は540分（600-60）");

            // 検証: 残業60分（540-480）
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(60, ot.regularOvertimeMinutes(), "通常残業は60分（540-480）");
        }

        @Test
        @DisplayName("nullレコードでIllegalArgumentExceptionまたはNullPointerException")
        void nullRecord_throwsException() {
            // 実行・検証: nullを渡すと例外
            assertThrows(NullPointerException.class, () -> calculator.calculateForFixed(null));
        }

        @Test
        @DisplayName("所定労働時間未満: 9:00-16:00（休憩1時間）で実労働6時間、残業0分")
        void underScheduled_noOvertime() {
            // 準備: 所定8時間に対して6時間勤務（早退）
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 9, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 23, 12, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 16, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: 実労働360分 < 所定480分 → 残業0分
            WorkDuration wd = result.workDuration();
            assertEquals(480, wd.scheduledMinutes(), "所定勤務時間は480分");
            assertEquals(420, wd.actualMinutes(), "実勤務時間は420分（7時間 = 9:00-16:00）");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(360, wd.netWorkMinutes(), "実労働時間は360分（420-60）");

            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(0, ot.regularOvertimeMinutes(), "通常残業は0分（所定未満）");
            assertEquals(0, ot.totalOvertimeMinutes(), "合計残業は0分");
        }
    }

    // ========================
    // シフト制のテスト
    // ========================

    @Nested
    @DisplayName("シフト制（calculateForShift）")
    class ShiftWorkTypeTest {

        @Test
        @DisplayName("6時間シフト: 10:00-17:00（休憩1時間）で実労働6時間、残業0分")
        void sixHourShift_noOvertime() {
            // 準備: 所定360分（6時間）のシフト
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            int scheduledMinutes = 360;
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 10, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 14, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 17, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForShift(record, scheduledMinutes);

            // 検証: WorkDuration
            WorkDuration wd = result.workDuration();
            assertEquals(360, wd.scheduledMinutes(), "所定勤務時間は360分（シフトの6時間）");
            assertEquals(420, wd.actualMinutes(), "実勤務時間は420分（7時間 = 10:00-17:00）");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(360, wd.netWorkMinutes(), "実労働時間は360分（420-60）");

            // 検証: OvertimeDuration — 所定丁度、残業なし
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(0, ot.regularOvertimeMinutes(), "通常残業は0分");
            assertEquals(0, ot.totalOvertimeMinutes(), "合計残業は0分");
        }

        @Test
        @DisplayName("10時間シフト: 8:00-20:00（休憩2時間）で実労働10時間、残業0分")
        void tenHourShift_noOvertime() {
            // 準備: 所定600分（10時間）のシフト
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            int scheduledMinutes = 600;
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 8, 0)),
                    // 昼休憩: 12:00-13:00（60分）
                    breakStart(LocalDateTime.of(2026, 2, 23, 12, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    // 夕方休憩: 17:00-18:00（60分）
                    breakStart(LocalDateTime.of(2026, 2, 23, 17, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 18, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 20, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForShift(record, scheduledMinutes);

            // 検証: WorkDuration
            WorkDuration wd = result.workDuration();
            assertEquals(600, wd.scheduledMinutes(), "所定勤務時間は600分（シフトの10時間）");
            assertEquals(720, wd.actualMinutes(), "実勤務時間は720分（12時間 = 8:00-20:00）");
            assertEquals(120, wd.breakMinutes(), "休憩時間は120分（60+60）");
            assertEquals(600, wd.netWorkMinutes(), "実労働時間は600分（720-120）");

            // 検証: OvertimeDuration — 所定丁度
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(0, ot.regularOvertimeMinutes(), "通常残業は0分（所定丁度）");
        }

        @Test
        @DisplayName("シフト超過: 所定6時間に対して7時間勤務で残業60分")
        void shiftOvertime_sixtyMinutes() {
            // 準備: 所定360分（6時間）のシフトで7時間勤務
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            int scheduledMinutes = 360;
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 10, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 14, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 18, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForShift(record, scheduledMinutes);

            // 検証: 実労働420分、所定360分 → 残業60分
            WorkDuration wd = result.workDuration();
            assertEquals(360, wd.scheduledMinutes(), "所定勤務時間は360分");
            assertEquals(480, wd.actualMinutes(), "実勤務時間は480分（8時間 = 10:00-18:00）");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(420, wd.netWorkMinutes(), "実労働時間は420分（480-60）");

            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(60, ot.regularOvertimeMinutes(), "通常残業は60分（420-360）");
            assertEquals(60, ot.totalOvertimeMinutes(), "合計残業は60分");
        }

        @Test
        @DisplayName("夜勤シフト: 22:00-翌7:00（休憩1時間）で深夜帯420分")
        void nightShift_lateNightCalculation() {
            // 準備: 夜勤シフト 22:00-翌7:00（所定480分）
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            int scheduledMinutes = 480;
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 22, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 24, 2, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 24, 3, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 24, 7, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForShift(record, scheduledMinutes);

            // 検証: WorkDuration
            WorkDuration wd = result.workDuration();
            assertEquals(480, wd.scheduledMinutes(), "所定勤務時間は480分");
            assertEquals(540, wd.actualMinutes(), "実勤務時間は540分（9時間 = 22:00-翌7:00）");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(480, wd.netWorkMinutes(), "実労働時間は480分（540-60）");

            // 検証: OvertimeDuration — 深夜帯22:00-翌5:00 = 420分
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(0, ot.regularOvertimeMinutes(), "通常残業は0分（所定丁度）");
            assertEquals(420, ot.lateNightMinutes(), "深夜勤務は420分（22:00-翌5:00）");
        }

        @Test
        @DisplayName("短時間シフト: 所定240分（4時間）で正確に計算")
        void shortShift_fourHours() {
            // 準備: 所定240分（4時間）のパートタイムシフト
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            int scheduledMinutes = 240;
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 9, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 13, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForShift(record, scheduledMinutes);

            // 検証: 実労働240分 = 所定240分、残業なし
            WorkDuration wd = result.workDuration();
            assertEquals(240, wd.scheduledMinutes(), "所定勤務時間は240分（4時間）");
            assertEquals(240, wd.actualMinutes(), "実勤務時間は240分");
            assertEquals(0, wd.breakMinutes(), "休憩時間は0分");
            assertEquals(240, wd.netWorkMinutes(), "実労働時間は240分");

            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(0, ot.regularOvertimeMinutes(), "通常残業は0分");
        }

        @Test
        @DisplayName("日曜シフト: 休日扱いで全労働時間が休日勤務")
        void sundayShift_holidayWork() {
            // 準備: 日曜日のシフト勤務（所定360分）
            LocalDate workDate = LocalDate.of(2026, 2, 22); // 日曜日
            int scheduledMinutes = 360;
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 22, 10, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 22, 13, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 22, 14, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 22, 17, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForShift(record, scheduledMinutes);

            // 検証: 実労働360分、全てが休日勤務
            WorkDuration wd = result.workDuration();
            assertEquals(360, wd.netWorkMinutes(), "実労働時間は360分");

            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(360, ot.holidayMinutes(), "休日勤務は360分（全労働時間）");
            assertEquals(360, ot.totalOvertimeMinutes(), "合計残業は360分（休日は全労働時間）");
        }

        @Test
        @DisplayName("nullレコードでNullPointerException")
        void nullRecord_throwsException() {
            assertThrows(NullPointerException.class,
                    () -> calculator.calculateForShift(null, 480));
        }
    }

    // ========================
    // フレックスタイム制のテスト
    // ========================

    @Nested
    @DisplayName("フレックスタイム制（calculateForFlex）")
    class FlexWorkTypeTest {

        @Test
        @DisplayName("標準フレックス: 10:00-19:00（休憩1時間）で日次残業は0分")
        void flexWork_noDaily_overtime() {
            // 準備: フレックスタイム制、10:00出勤19:00退勤
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 10, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 14, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 19, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFlex(record);

            // 検証: WorkDuration — 所定は0（フレックスは月次精算のため）
            WorkDuration wd = result.workDuration();
            assertEquals(0, wd.scheduledMinutes(), "所定勤務時間は0分（フレックスは月次精算）");
            assertEquals(540, wd.actualMinutes(), "実勤務時間は540分（9時間 = 10:00-19:00）");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(480, wd.netWorkMinutes(), "実労働時間は480分（540-60）");

            // 検証: OvertimeDuration — 日次では残業ゼロ
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(0, ot.regularOvertimeMinutes(), "通常残業は0分（フレックスは日次で残業計算しない）");
            assertEquals(0, ot.lateNightMinutes(), "深夜勤務は0分（19:00退勤）");
            assertEquals(0, ot.holidayMinutes(), "休日勤務は0分");
            assertEquals(0, ot.totalOvertimeMinutes(), "合計残業は0分");
        }

        @Test
        @DisplayName("フレックス長時間勤務: 8:00-22:00でも日次残業0分、深夜は計上不可（22:00退勤は深夜帯外）")
        void flexLongWork_noDailyOvertime() {
            // 準備: 長時間勤務だがフレックスなので日次残業なし
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 8, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 23, 12, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 22, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFlex(record);

            // 検証: WorkDuration
            WorkDuration wd = result.workDuration();
            assertEquals(0, wd.scheduledMinutes(), "所定勤務時間は0分（フレックス）");
            assertEquals(840, wd.actualMinutes(), "実勤務時間は840分（14時間 = 8:00-22:00）");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(780, wd.netWorkMinutes(), "実労働時間は780分（840-60）");

            // 検証: OvertimeDuration — 日次残業なし、深夜も0分（22:00ちょうど退勤は深夜帯に含まれない）
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(0, ot.regularOvertimeMinutes(), "通常残業は0分（フレックス）");
            assertEquals(0, ot.lateNightMinutes(), "深夜勤務は0分（22:00退勤、深夜帯に入らない）");
            assertEquals(0, ot.totalOvertimeMinutes(), "合計残業は0分");
        }

        @Test
        @DisplayName("フレックスで深夜帯にかかる場合: 深夜時間だけは日次で計算される")
        void flexWithLateNight_lateNightCalculated() {
            // 準備: 深夜帯にかかるフレックス勤務
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 14, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 23, 18, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 19, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 23, 30))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFlex(record);

            // 検証: WorkDuration
            WorkDuration wd = result.workDuration();
            assertEquals(0, wd.scheduledMinutes(), "所定勤務時間は0分（フレックス）");
            assertEquals(570, wd.actualMinutes(), "実勤務時間は570分（9.5時間 = 14:00-23:30）");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(510, wd.netWorkMinutes(), "実労働時間は510分（570-60）");

            // 検証: OvertimeDuration — 日次残業は0分だが深夜は計上される
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(0, ot.regularOvertimeMinutes(), "通常残業は0分（フレックス）");
            assertEquals(90, ot.lateNightMinutes(), "深夜勤務は90分（22:00-23:30）");
            assertEquals(0, ot.holidayMinutes(), "休日勤務は0分");
            assertEquals(0, ot.totalOvertimeMinutes(), "合計残業は0分（フレックスは日次残業なし）");
        }

        @Test
        @DisplayName("フレックス短時間勤務: 10:00-14:00（休憩なし）で実労働4時間、残業0分")
        void flexShortWork() {
            // 準備: 短時間のフレックス勤務
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 10, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 14, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFlex(record);

            // 検証: WorkDuration
            WorkDuration wd = result.workDuration();
            assertEquals(0, wd.scheduledMinutes(), "所定勤務時間は0分（フレックス）");
            assertEquals(240, wd.actualMinutes(), "実勤務時間は240分（4時間）");
            assertEquals(0, wd.breakMinutes(), "休憩時間は0分");
            assertEquals(240, wd.netWorkMinutes(), "実労働時間は240分");

            // 検証: OvertimeDuration — 全てゼロ
            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(0, ot.regularOvertimeMinutes(), "通常残業は0分");
            assertEquals(0, ot.lateNightMinutes(), "深夜勤務は0分");
            assertEquals(0, ot.totalOvertimeMinutes(), "合計残業は0分");
        }

        @Test
        @DisplayName("フレックスで複数回休憩: 休憩合計が正しく控除される")
        void flexMultipleBreaks() {
            // 準備: 2回の休憩を含むフレックス勤務
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 9, 0)),
                    // 午前休憩: 10:30-10:45（15分）
                    breakStart(LocalDateTime.of(2026, 2, 23, 10, 30)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 10, 45)),
                    // 昼休憩: 12:00-13:00（60分）
                    breakStart(LocalDateTime.of(2026, 2, 23, 12, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 18, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFlex(record);

            // 検証: 休憩合計75分が正しく控除
            WorkDuration wd = result.workDuration();
            assertEquals(540, wd.actualMinutes(), "実勤務時間は540分（9時間 = 9:00-18:00）");
            assertEquals(75, wd.breakMinutes(), "休憩時間は75分（15+60）");
            assertEquals(465, wd.netWorkMinutes(), "実労働時間は465分（540-75）");
        }

        @Test
        @DisplayName("nullレコードでNullPointerException")
        void nullRecord_throwsException() {
            assertThrows(NullPointerException.class, () -> calculator.calculateForFlex(null));
        }
    }

    // ========================
    // 深夜時間帯の詳細テスト
    // ========================

    @Nested
    @DisplayName("深夜時間帯の計算（22:00-5:00）")
    class LateNightCalculationTest {

        @Test
        @DisplayName("22:00ちょうどに退勤: 深夜帯は0分（22:00は含まれない）")
        void clockOutAt2200_zeroLateNight() {
            // 準備: 22:00ちょうど退勤 — 深夜帯の開始時刻だが、22:00-22:00で0分
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 14, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 22, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: 深夜帯0分
            assertEquals(0, result.overtimeDuration().lateNightMinutes(),
                    "深夜勤務は0分（22:00ちょうど退勤は深夜帯に入らない）");
        }

        @Test
        @DisplayName("22:30退勤: 深夜帯30分（22:00-22:30）")
        void clockOutAt2230_thirtyMinutesLateNight() {
            // 準備: 22:30退勤
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 14, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 22, 30))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: 深夜帯30分
            assertEquals(30, result.overtimeDuration().lateNightMinutes(),
                    "深夜勤務は30分（22:00-22:30）");
        }

        @Test
        @DisplayName("翌5:00ちょうど退勤: 深夜帯420分（22:00-翌5:00の全体）")
        void clockOutAt0500_fullLateNight() {
            // 準備: 夜勤で翌5:00退勤
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 21, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 24, 5, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: 深夜帯420分（22:00-翌5:00 = 7時間）
            assertEquals(420, result.overtimeDuration().lateNightMinutes(),
                    "深夜勤務は420分（22:00-翌5:00 = 7時間）");
        }

        @Test
        @DisplayName("早朝5:00以前に出勤: 深夜帯の早朝部分のみ計上")
        void earlyMorningStart_lateNightBeforeFive() {
            // 準備: 4:00出勤、12:00退勤（深夜帯 4:00-5:00 = 60分）
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 4, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 12, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: 深夜帯60分（4:00-5:00）
            assertEquals(60, result.overtimeDuration().lateNightMinutes(),
                    "深夜勤務は60分（4:00-5:00の深夜帯）");
        }

        @Test
        @DisplayName("完全に深夜帯内の勤務: 23:00-翌4:00で深夜300分")
        void entirelyWithinLateNight() {
            // 準備: 深夜帯のみの勤務 23:00-翌4:00
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 23, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 24, 4, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: 深夜帯300分（23:00-翌4:00 = 5時間）
            assertEquals(300, result.overtimeDuration().lateNightMinutes(),
                    "深夜勤務は300分（23:00-翌4:00 = 5時間）");
        }
    }

    // ========================
    // エッジケースのテスト
    // ========================

    @Nested
    @DisplayName("エッジケース")
    class EdgeCaseTest {

        @Test
        @DisplayName("打刻修正: 出勤時刻を修正した場合、最新の修正エントリが使用される")
        void clockInCorrection_usesLatestEntry() {
            // 準備: 出勤時刻を9:00→8:30に修正
            LocalDate workDate = LocalDate.of(2026, 2, 23); // 月曜日
            List<ClockEntry> entries = new ArrayList<>(List.of(
                    clockIn(LocalDateTime.of(2026, 2, 23, 9, 0)),
                    breakStart(LocalDateTime.of(2026, 2, 23, 12, 0)),
                    breakEnd(LocalDateTime.of(2026, 2, 23, 13, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 18, 0)),
                    // 出勤時刻を8:30に修正
                    correctionEntry(ClockType.CLOCK_IN, LocalDateTime.of(2026, 2, 23, 8, 30))
            ));
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: 修正後の8:30出勤で計算される（8:30-18:00 = 570分）
            WorkDuration wd = result.workDuration();
            assertEquals(570, wd.actualMinutes(), "実勤務時間は570分（9.5時間 = 8:30-18:00）");
            assertEquals(60, wd.breakMinutes(), "休憩時間は60分");
            assertEquals(510, wd.netWorkMinutes(), "実労働時間は510分（570-60）");

            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(30, ot.regularOvertimeMinutes(), "通常残業は30分（510-480）");
        }

        @Test
        @DisplayName("CalculationResultのnull検証: WorkDurationがnullで例外")
        void calculationResult_nullWorkDuration_throws() {
            assertThrows(NullPointerException.class,
                    () -> new WorkDurationCalculator.CalculationResult(null, OvertimeDuration.zero()));
        }

        @Test
        @DisplayName("CalculationResultのnull検証: OvertimeDurationがnullで例外")
        void calculationResult_nullOvertimeDuration_throws() {
            assertThrows(NullPointerException.class,
                    () -> new WorkDurationCalculator.CalculationResult(WorkDuration.zero(), null));
        }

        @Test
        @DisplayName("日曜深夜勤務: 休日かつ深夜の両方が計上される")
        void sundayLateNight_bothHolidayAndLateNight() {
            // 準備: 日曜日の夜勤 20:00-翌1:00
            LocalDate workDate = LocalDate.of(2026, 2, 22); // 日曜日
            List<ClockEntry> entries = List.of(
                    clockIn(LocalDateTime.of(2026, 2, 22, 20, 0)),
                    clockOut(LocalDateTime.of(2026, 2, 23, 1, 0))
            );
            AttendanceRecord record = createRecord(workDate, entries);

            // 実行
            WorkDurationCalculator.CalculationResult result = calculator.calculateForFixed(record);

            // 検証: 深夜帯 22:00-翌1:00 = 180分、休日勤務 = 300分（全労働時間）
            WorkDuration wd = result.workDuration();
            assertEquals(300, wd.actualMinutes(), "実勤務時間は300分（5時間 = 20:00-翌1:00）");
            assertEquals(300, wd.netWorkMinutes(), "実労働時間は300分");

            OvertimeDuration ot = result.overtimeDuration();
            assertEquals(180, ot.lateNightMinutes(), "深夜勤務は180分（22:00-翌1:00）");
            assertEquals(300, ot.holidayMinutes(), "休日勤務は300分（全労働時間）");
            assertEquals(300, ot.totalOvertimeMinutes(), "合計残業は300分（休日は全労働時間）");
        }

        @Test
        @DisplayName("CalculationResultのアクセサが正しい値を返す")
        void calculationResult_accessors() {
            // 準備
            WorkDuration wd = WorkDuration.of(480, 540, 60, 480);
            OvertimeDuration ot = OvertimeDuration.of(0, 0, 0, 0);

            // 実行
            WorkDurationCalculator.CalculationResult result =
                    new WorkDurationCalculator.CalculationResult(wd, ot);

            // 検証: recordのアクセサが正しい値を返す
            assertSame(wd, result.workDuration(), "workDuration()が同一インスタンスを返す");
            assertSame(ot, result.overtimeDuration(), "overtimeDuration()が同一インスタンスを返す");
        }
    }
}
