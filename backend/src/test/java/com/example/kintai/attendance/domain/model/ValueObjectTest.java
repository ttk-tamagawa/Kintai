package com.example.kintai.attendance.domain.model;

import com.example.kintai.attendance.domain.model.shift.PatternName;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 値オブジェクト（VO）のユニットテスト
 *
 * <p>各VOのバリデーション・等価性・不変性を検証する。
 * Spring / Mockito を使用しない純粋なユニットテスト。</p>
 */
@DisplayName("値オブジェクト テスト")
class ValueObjectTest {

    // ========================
    // ClockTime テスト
    // ========================

    @Nested
    @DisplayName("ClockTime（打刻時刻VO）")
    class ClockTimeTest {

        @Test
        @DisplayName("正常系: Instantを指定して生成できる")
        void 正常に生成できる() {
            // 準備: 現在時刻のInstantを用意
            Instant now = Instant.now();

            // 実行: ClockTimeを生成
            ClockTime clockTime = new ClockTime(now);

            // 検証: 値が保持されていることを確認
            assertEquals(now, clockTime.value());
        }

        @Test
        @DisplayName("異常系: nullを渡すとNullPointerExceptionが発生する")
        void nullを拒否する() {
            // 実行・検証: nullでNPEが発生することを確認
            NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> new ClockTime(null)
            );
            assertEquals("打刻時刻はnullにできません", exception.getMessage());
        }

        @Test
        @DisplayName("等価性: 同じInstantを持つClockTimeは等しい")
        void 同じ値なら等しい() {
            // 準備: 同一のInstantを用意
            Instant now = Instant.parse("2026-02-23T09:00:00Z");

            // 実行: 2つのClockTimeを生成
            ClockTime clockTime1 = new ClockTime(now);
            ClockTime clockTime2 = new ClockTime(now);

            // 検証: recordの等価性で一致することを確認
            assertEquals(clockTime1, clockTime2);
            assertEquals(clockTime1.hashCode(), clockTime2.hashCode());
        }
    }

    // ========================
    // WorkDate テスト
    // ========================

    @Nested
    @DisplayName("WorkDate（勤務日VO）")
    class WorkDateTest {

        @Test
        @DisplayName("正常系: LocalDateを指定して生成できる")
        void 正常に生成できる() {
            // 準備: 勤務日を用意
            java.time.LocalDate date = java.time.LocalDate.of(2026, 2, 23);

            // 実行: WorkDateを生成
            WorkDate workDate = new WorkDate(date);

            // 検証: 値が保持されていることを確認
            assertEquals(date, workDate.value());
        }

        @Test
        @DisplayName("異常系: nullを渡すとNullPointerExceptionが発生する")
        void nullを拒否する() {
            // 実行・検証: nullでNPEが発生することを確認
            NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> new WorkDate(null)
            );
            assertEquals("勤務日はnullにできません", exception.getMessage());
        }

        @Test
        @DisplayName("夜勤判定: すべての日付は夜勤開始日となりうる")
        void 夜勤判定はtrueを返す() {
            // 準備: 任意の勤務日
            WorkDate workDate = new WorkDate(java.time.LocalDate.of(2026, 2, 23));

            // 実行・検証: 夜勤開始日の可能性はtrue
            assertTrue(workDate.isNightShiftDate());
        }
    }

    // ========================
    // PatternName テスト
    // ========================

    @Nested
    @DisplayName("PatternName（パターン名VO）")
    class PatternNameTest {

        @Test
        @DisplayName("正常系: 2文字（最小長）で生成できる")
        void 最小文字数で生成できる() {
            // 実行: 2文字のパターン名を生成
            PatternName name = new PatternName("早番");

            // 検証: 値が保持されていることを確認
            assertEquals("早番", name.value());
        }

        @Test
        @DisplayName("正常系: 20文字（最大長）で生成できる")
        void 最大文字数で生成できる() {
            // 準備: 20文字の文字列を用意
            String twentyChars = "あ".repeat(20);

            // 実行: 20文字のパターン名を生成
            PatternName name = new PatternName(twentyChars);

            // 検証: 値が保持されていることを確認
            assertEquals(twentyChars, name.value());
        }

        @Test
        @DisplayName("異常系: 1文字（最小長未満）はIllegalArgumentExceptionが発生する")
        void 短すぎると例外が発生する() {
            // 実行・検証: 1文字で例外が発生することを確認
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new PatternName("あ")
            );
            assertTrue(exception.getMessage().contains("2"));
            assertTrue(exception.getMessage().contains("20"));
        }

        @Test
        @DisplayName("異常系: 21文字（最大長超過）はIllegalArgumentExceptionが発生する")
        void 長すぎると例外が発生する() {
            // 準備: 21文字の文字列を用意
            String twentyOneChars = "あ".repeat(21);

            // 実行・検証: 21文字で例外が発生することを確認
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new PatternName(twentyOneChars)
            );
            assertTrue(exception.getMessage().contains("2"));
            assertTrue(exception.getMessage().contains("20"));
        }

        @Test
        @DisplayName("異常系: nullはNullPointerExceptionが発生する")
        void nullを拒否する() {
            // 実行・検証: nullでNPEが発生することを確認
            assertThrows(
                    NullPointerException.class,
                    () -> new PatternName(null)
            );
        }

        @Test
        @DisplayName("異常系: 空文字はIllegalArgumentExceptionが発生する")
        void 空文字を拒否する() {
            // 実行・検証: 空文字で例外が発生することを確認
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PatternName("")
            );
        }

        @Test
        @DisplayName("異常系: 空白のみはIllegalArgumentExceptionが発生する")
        void 空白のみを拒否する() {
            // 実行・検証: 空白文字列で例外が発生することを確認
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PatternName("   ")
            );
        }
    }

    // ========================
    // 型安全ID テスト
    // ========================

    @Nested
    @DisplayName("型安全ID（AttendanceRecordId, EmployeeId, ShiftPatternId, ScheduleId）")
    class TypeSafeIdTest {

        @Nested
        @DisplayName("AttendanceRecordId")
        class AttendanceRecordIdTest {

            @Test
            @DisplayName("generate(): ランダムなIDが生成される")
            void generateでIDが生成される() {
                // 実行: IDを自動生成
                AttendanceRecordId id = AttendanceRecordId.generate();

                // 検証: nullでないUUIDが生成されていること
                assertNotNull(id);
                assertNotNull(id.value());
            }

            @Test
            @DisplayName("generate(): 毎回異なるIDが生成される")
            void generateで毎回異なるIDが生成される() {
                // 実行: 2つのIDを生成
                AttendanceRecordId id1 = AttendanceRecordId.generate();
                AttendanceRecordId id2 = AttendanceRecordId.generate();

                // 検証: 異なるIDであること
                assertNotEquals(id1, id2);
            }

            @Test
            @DisplayName("of(): 指定したUUIDからIDを生成できる")
            void ofでUUIDからIDを生成できる() {
                // 準備: UUIDを用意
                UUID uuid = UUID.randomUUID();

                // 実行: UUIDからIDを生成
                AttendanceRecordId id = AttendanceRecordId.of(uuid);

                // 検証: 指定したUUIDが保持されていること
                assertEquals(uuid, id.value());
            }

            @Test
            @DisplayName("value(): 保持しているUUIDを取得できる")
            void valueでUUIDを取得できる() {
                // 準備: UUIDを用意してIDを生成
                UUID uuid = UUID.randomUUID();
                AttendanceRecordId id = new AttendanceRecordId(uuid);

                // 検証: value()で同じUUIDが返されること
                assertEquals(uuid, id.value());
            }

            @Test
            @DisplayName("異常系: nullを渡すとNullPointerExceptionが発生する")
            void nullを拒否する() {
                // 実行・検証: nullでNPEが発生すること
                assertThrows(
                        NullPointerException.class,
                        () -> new AttendanceRecordId(null)
                );
            }
        }

        @Nested
        @DisplayName("EmployeeId")
        class EmployeeIdTest {

            @Test
            @DisplayName("generate(): ランダムなIDが生成される")
            void generateでIDが生成される() {
                EmployeeId id = EmployeeId.generate();
                assertNotNull(id);
                assertNotNull(id.value());
            }

            @Test
            @DisplayName("of(): 指定したUUIDからIDを生成できる")
            void ofでUUIDからIDを生成できる() {
                UUID uuid = UUID.randomUUID();
                EmployeeId id = EmployeeId.of(uuid);
                assertEquals(uuid.toString(), id.value());
            }

            @Test
            @DisplayName("value(): 保持している文字列を取得できる")
            void valueで文字列を取得できる() {
                String value = UUID.randomUUID().toString();
                EmployeeId id = new EmployeeId(value);
                assertEquals(value, id.value());
            }

            @Test
            @DisplayName("異常系: nullを渡すとNullPointerExceptionが発生する")
            void nullを拒否する() {
                assertThrows(
                        NullPointerException.class,
                        () -> new EmployeeId(null)
                );
            }
        }

        @Nested
        @DisplayName("ShiftPatternId")
        class ShiftPatternIdTest {

            @Test
            @DisplayName("generate(): ランダムなIDが生成される")
            void generateでIDが生成される() {
                ShiftPatternId id = ShiftPatternId.generate();
                assertNotNull(id);
                assertNotNull(id.value());
            }

            @Test
            @DisplayName("of(): 指定したUUIDからIDを生成できる")
            void ofでUUIDからIDを生成できる() {
                UUID uuid = UUID.randomUUID();
                ShiftPatternId id = ShiftPatternId.of(uuid);
                assertEquals(uuid, id.value());
            }

            @Test
            @DisplayName("value(): 保持しているUUIDを取得できる")
            void valueでUUIDを取得できる() {
                UUID uuid = UUID.randomUUID();
                ShiftPatternId id = new ShiftPatternId(uuid);
                assertEquals(uuid, id.value());
            }

            @Test
            @DisplayName("異常系: nullを渡すとNullPointerExceptionが発生する")
            void nullを拒否する() {
                assertThrows(
                        NullPointerException.class,
                        () -> new ShiftPatternId(null)
                );
            }
        }

        @Nested
        @DisplayName("ScheduleId")
        class ScheduleIdTest {

            @Test
            @DisplayName("generate(): ランダムなIDが生成される")
            void generateでIDが生成される() {
                ScheduleId id = ScheduleId.generate();
                assertNotNull(id);
                assertNotNull(id.value());
            }

            @Test
            @DisplayName("of(): 指定したUUIDからIDを生成できる")
            void ofでUUIDからIDを生成できる() {
                UUID uuid = UUID.randomUUID();
                ScheduleId id = ScheduleId.of(uuid);
                assertEquals(uuid, id.value());
            }

            @Test
            @DisplayName("value(): 保持しているUUIDを取得できる")
            void valueでUUIDを取得できる() {
                UUID uuid = UUID.randomUUID();
                ScheduleId id = new ScheduleId(uuid);
                assertEquals(uuid, id.value());
            }

            @Test
            @DisplayName("異常系: nullを渡すとNullPointerExceptionが発生する")
            void nullを拒否する() {
                assertThrows(
                        NullPointerException.class,
                        () -> new ScheduleId(null)
                );
            }
        }

        @Test
        @DisplayName("型安全性: 異なるID型は混同できない（コンパイル時保証）")
        void 異なるID型は等しくならない() {
            // 準備: 同じUUIDで異なる型のIDを生成
            UUID uuid = UUID.randomUUID();
            AttendanceRecordId attendanceId = AttendanceRecordId.of(uuid);
            EmployeeId employeeId = EmployeeId.of(uuid);

            // 検証: 同じUUIDでも異なる型なので等しくならない
            assertNotEquals(attendanceId, employeeId);
        }
    }

    // ========================
    // WorkDuration テスト
    // ========================

    @Nested
    @DisplayName("WorkDuration（勤務時間VO）")
    class WorkDurationTest {

        @Test
        @DisplayName("正常系: ofファクトリメソッドで生成できる")
        void ofで生成できる() {
            // 実行: 所定480分、実540分、休憩60分、実労働480分
            WorkDuration duration = WorkDuration.of(480, 540, 60, 480);

            // 検証: 各フィールドが正しく保持されていること
            assertEquals(480, duration.scheduledMinutes());
            assertEquals(540, duration.actualMinutes());
            assertEquals(60, duration.breakMinutes());
            assertEquals(480, duration.netWorkMinutes());
        }

        @Test
        @DisplayName("正常系: zeroファクトリメソッドで全てゼロのインスタンスが生成される")
        void zeroで全てゼロのインスタンスが生成される() {
            // 実行: ゼロ値のWorkDurationを生成
            WorkDuration duration = WorkDuration.zero();

            // 検証: 全フィールドがゼロであること
            assertEquals(0, duration.scheduledMinutes());
            assertEquals(0, duration.actualMinutes());
            assertEquals(0, duration.breakMinutes());
            assertEquals(0, duration.netWorkMinutes());
        }

        @Test
        @DisplayName("等価性: 同じ値を持つWorkDurationは等しい（record等価性）")
        void 同じ値なら等しい() {
            // 実行: 同じ値で2つのインスタンスを生成
            WorkDuration d1 = WorkDuration.of(480, 540, 60, 480);
            WorkDuration d2 = WorkDuration.of(480, 540, 60, 480);

            // 検証: recordの等価性で一致すること
            assertEquals(d1, d2);
            assertEquals(d1.hashCode(), d2.hashCode());
        }

        @Test
        @DisplayName("等価性: 異なる値を持つWorkDurationは等しくない")
        void 異なる値なら等しくない() {
            // 実行: 異なる値で2つのインスタンスを生成
            WorkDuration d1 = WorkDuration.of(480, 540, 60, 480);
            WorkDuration d2 = WorkDuration.of(480, 600, 60, 540);

            // 検証: 異なるインスタンスは等しくないこと
            assertNotEquals(d1, d2);
        }
    }

    // ========================
    // OvertimeDuration テスト
    // ========================

    @Nested
    @DisplayName("OvertimeDuration（残業時間VO）")
    class OvertimeDurationTest {

        @Test
        @DisplayName("正常系: ofファクトリメソッドで生成できる")
        void ofで生成できる() {
            // 実行: 通常残業60分、深夜30分、休日0分、合計90分
            OvertimeDuration overtime = OvertimeDuration.of(60, 30, 0, 90);

            // 検証: 各フィールドが正しく保持されていること
            assertEquals(60, overtime.regularOvertimeMinutes());
            assertEquals(30, overtime.lateNightMinutes());
            assertEquals(0, overtime.holidayMinutes());
            assertEquals(90, overtime.totalOvertimeMinutes());
        }

        @Test
        @DisplayName("正常系: zeroファクトリメソッドで全てゼロのインスタンスが生成される")
        void zeroで全てゼロのインスタンスが生成される() {
            // 実行: ゼロ値のOvertimeDurationを生成
            OvertimeDuration overtime = OvertimeDuration.zero();

            // 検証: 全フィールドがゼロであること
            assertEquals(0, overtime.regularOvertimeMinutes());
            assertEquals(0, overtime.lateNightMinutes());
            assertEquals(0, overtime.holidayMinutes());
            assertEquals(0, overtime.totalOvertimeMinutes());
        }

        @Test
        @DisplayName("totalOvertimeMinutes: 合計残業時間が正しく取得できる")
        void 合計残業時間が正しく計算される() {
            // 準備: 通常残業120分、深夜60分、休日480分、合計660分
            OvertimeDuration overtime = OvertimeDuration.of(120, 60, 480, 660);

            // 検証: totalOvertimeMinutesが正しいこと
            assertEquals(660, overtime.totalOvertimeMinutes());
        }

        @Test
        @DisplayName("等価性: 同じ値を持つOvertimeDurationは等しい（record等価性）")
        void 同じ値なら等しい() {
            // 実行: 同じ値で2つのインスタンスを生成
            OvertimeDuration o1 = OvertimeDuration.of(60, 30, 0, 90);
            OvertimeDuration o2 = OvertimeDuration.of(60, 30, 0, 90);

            // 検証: recordの等価性で一致すること
            assertEquals(o1, o2);
            assertEquals(o1.hashCode(), o2.hashCode());
        }
    }

    // ========================
    // ClockEntry テスト
    // ========================

    @Nested
    @DisplayName("ClockEntry（打刻エントリVO）")
    class ClockEntryTest {

        /** テスト用のClockEntryを生成するヘルパーメソッド */
        private ClockEntry createClockEntry(ClockType type, ClockSource source) {
            return new ClockEntry(type, new ClockTime(Instant.now()), source);
        }

        @Test
        @DisplayName("正常系: 全フィールドを指定して生成できる")
        void 正常に生成できる() {
            // 準備: 出勤打刻の各要素を用意
            ClockType type = ClockType.CLOCK_IN;
            ClockTime time = new ClockTime(Instant.parse("2026-02-23T00:00:00Z"));
            ClockSource source = ClockSource.WEB;

            // 実行: ClockEntryを生成
            ClockEntry entry = new ClockEntry(type, time, source);

            // 検証: 各フィールドが正しく保持されていること
            assertEquals(ClockType.CLOCK_IN, entry.type());
            assertEquals(time, entry.time());
            assertEquals(ClockSource.WEB, entry.source());
        }

        @Test
        @DisplayName("不変性: recordのためフィールドは変更できない（コンパイル時保証）")
        void recordとして不変である() {
            // 準備: ClockEntryを生成
            Instant originalInstant = Instant.parse("2026-02-23T09:00:00Z");
            ClockTime originalTime = new ClockTime(originalInstant);
            ClockEntry entry = new ClockEntry(ClockType.CLOCK_IN, originalTime, ClockSource.WEB);

            // 検証: 生成後も値が変わらないことを確認
            assertEquals(ClockType.CLOCK_IN, entry.type());
            assertEquals(originalTime, entry.time());
            assertEquals(ClockSource.WEB, entry.source());
        }

        @Test
        @DisplayName("等価性: 同じ値を持つClockEntryは等しい")
        void 同じ値なら等しい() {
            // 準備: 同じ打刻時刻を用意
            Instant time = Instant.parse("2026-02-23T09:00:00Z");
            ClockTime clockTime = new ClockTime(time);

            // 実行: 同じ値で2つのインスタンスを生成
            ClockEntry e1 = new ClockEntry(ClockType.CLOCK_IN, clockTime, ClockSource.WEB);
            ClockEntry e2 = new ClockEntry(ClockType.CLOCK_IN, clockTime, ClockSource.WEB);

            // 検証: recordの等価性で一致すること
            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("異常系: typeがnullだとNullPointerExceptionが発生する")
        void typeがnullだと例外が発生する() {
            // 実行・検証: typeにnullを渡すとNPE
            NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> new ClockEntry(null, new ClockTime(Instant.now()), ClockSource.WEB)
            );
            assertEquals("打刻種別はnullにできません", exception.getMessage());
        }

        @Test
        @DisplayName("異常系: timeがnullだとNullPointerExceptionが発生する")
        void timeがnullだと例外が発生する() {
            // 実行・検証: timeにnullを渡すとNPE
            NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> new ClockEntry(ClockType.CLOCK_IN, null, ClockSource.WEB)
            );
            assertEquals("打刻時刻はnullにできません", exception.getMessage());
        }

        @Test
        @DisplayName("異常系: sourceがnullだとNullPointerExceptionが発生する")
        void sourceがnullだと例外が発生する() {
            // 実行・検証: sourceにnullを渡すとNPE
            NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> new ClockEntry(ClockType.CLOCK_IN, new ClockTime(Instant.now()), null)
            );
            assertEquals("打刻元はnullにできません", exception.getMessage());
        }

        @Test
        @DisplayName("全打刻種別: 4種類すべてのClockTypeで生成できる")
        void 全打刻種別で生成できる() {
            // 検証: 4種類すべてのClockTypeで正常に生成できること
            assertDoesNotThrow(() -> createClockEntry(ClockType.CLOCK_IN, ClockSource.WEB));
            assertDoesNotThrow(() -> createClockEntry(ClockType.CLOCK_OUT, ClockSource.WEB));
            assertDoesNotThrow(() -> createClockEntry(ClockType.BREAK_START, ClockSource.MOBILE));
            assertDoesNotThrow(() -> createClockEntry(ClockType.BREAK_END, ClockSource.MOBILE));
        }

        @Test
        @DisplayName("全打刻元: 4種類すべてのClockSourceで生成できる")
        void 全打刻元で生成できる() {
            // 検証: 4種類すべてのClockSourceで正常に生成できること
            assertDoesNotThrow(() -> createClockEntry(ClockType.CLOCK_IN, ClockSource.WEB));
            assertDoesNotThrow(() -> createClockEntry(ClockType.CLOCK_IN, ClockSource.MOBILE));
            assertDoesNotThrow(() -> createClockEntry(ClockType.CLOCK_IN, ClockSource.MANUAL));
            assertDoesNotThrow(() -> createClockEntry(ClockType.CLOCK_IN, ClockSource.CORRECTION));
        }
    }
}
