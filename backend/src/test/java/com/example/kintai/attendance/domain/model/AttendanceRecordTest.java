package com.example.kintai.attendance.domain.model;

import com.example.kintai.shared.domain.model.ApprovalId;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AttendanceRecord（勤怠記録 集約ルート）の単体テスト
 *
 * <p>状態遷移（NOT_CLOCKED → CLOCKED_IN → CLOCKED_OUT → FINALIZED）と
 * 7つのコマンドメソッドの正常系・異常系を網羅的に検証する。</p>
 */
@DisplayName("AttendanceRecord 集約ルート テスト")
class AttendanceRecordTest {

    // ========================
    // テスト用定数・ヘルパーメソッド
    // ========================

    /** テスト用の従業員ID */
    private static final EmployeeId TEST_EMPLOYEE_ID = EmployeeId.generate();

    /** テスト用のシフトパターンID */
    private static final ShiftPatternId TEST_SHIFT_PATTERN_ID = ShiftPatternId.generate();

    /** テスト用の勤務日（2026-02-23） */
    private static final WorkDate TEST_WORK_DATE = new WorkDate(LocalDate.of(2026, 2, 23));

    /** テスト用の打刻元（WEB） */
    private static final ClockSource TEST_SOURCE = ClockSource.WEB;

    /**
     * テスト用の打刻時刻を生成する
     *
     * @param hoursOffset 基準時刻からのオフセット（時間）
     * @return ClockTimeインスタンス
     */
    private static ClockTime clockTimeOf(int hoursOffset) {
        // 2026-02-23 00:00:00 UTC を基準にオフセットを加算
        return new ClockTime(Instant.parse("2026-02-23T00:00:00Z").plusSeconds(hoursOffset * 3600L));
    }

    /**
     * テスト用の承認IDを生成する
     *
     * @return ApprovalIdインスタンス
     */
    private static ApprovalId testApprovalId() {
        return ApprovalId.generate();
    }

    /**
     * 新規の勤怠記録（NOT_CLOCKED状態）を作成する
     *
     * @return 初期状態のAttendanceRecord
     */
    private static AttendanceRecord createNewRecord() {
        return AttendanceRecord.create(TEST_EMPLOYEE_ID, TEST_WORK_DATE, TEST_SHIFT_PATTERN_ID);
    }

    /**
     * CLOCKED_IN状態の勤怠記録を作成する（出勤打刻済み）
     *
     * @return 出勤中のAttendanceRecord
     */
    private static AttendanceRecord createClockedInRecord() {
        AttendanceRecord record = createNewRecord();
        record.clockIn(clockTimeOf(9), TEST_SOURCE); // 09:00 出勤
        return record;
    }

    /**
     * CLOCKED_OUT状態の勤怠記録を作成する（退勤打刻済み）
     *
     * @return 退勤済みのAttendanceRecord
     */
    private static AttendanceRecord createClockedOutRecord() {
        AttendanceRecord record = createClockedInRecord();
        record.clockOut(clockTimeOf(18), TEST_SOURCE); // 18:00 退勤
        return record;
    }

    /**
     * FINALIZED状態の勤怠記録を作成する（本締め確定済み）
     *
     * @return 確定済みのAttendanceRecord
     */
    private static AttendanceRecord createFinalizedRecord() {
        AttendanceRecord record = createClockedOutRecord();
        record.finalizeRecord("monthly-closing-001");
        return record;
    }

    // ========================
    // ファクトリメソッドのテスト
    // ========================

    @Nested
    @DisplayName("create - 新規作成ファクトリメソッド")
    class CreateTest {

        @Test
        @DisplayName("新規作成時にNOT_CLOCKED状態で初期化される")
        void shouldCreateWithNotClockedStatus() {
            // 新規勤怠記録を作成
            AttendanceRecord record = createNewRecord();

            // 初期状態の検証
            assertNotNull(record.getId(), "勤怠記録IDが自動生成されること");
            assertEquals(TEST_EMPLOYEE_ID, record.getEmployeeId(), "従業員IDが設定されること");
            assertEquals(TEST_WORK_DATE, record.getWorkDate(), "勤務日が設定されること");
            assertEquals(TEST_SHIFT_PATTERN_ID, record.getShiftPatternId(), "シフトパターンIDが設定されること");
            assertEquals(AttendanceStatus.NOT_CLOCKED, record.getStatus(), "ステータスがNOT_CLOCKEDであること");
            assertTrue(record.getClockEntries().isEmpty(), "打刻エントリが空であること");
            assertEquals(WorkDuration.zero(), record.getWorkDuration(), "勤務時間がゼロであること");
            assertEquals(OvertimeDuration.zero(), record.getOvertimeDuration(), "残業時間がゼロであること");
            assertEquals(0, record.getVersion(), "バージョンが0であること");
            assertNotNull(record.getCreatedAt(), "作成日時が設定されること");
            assertNotNull(record.getUpdatedAt(), "更新日時が設定されること");
        }

        @Test
        @DisplayName("シフトパターンIDがnullでも作成できる（シフト未割当）")
        void shouldCreateWithNullShiftPatternId() {
            // シフト未割当で新規作成
            AttendanceRecord record = AttendanceRecord.create(
                    TEST_EMPLOYEE_ID, TEST_WORK_DATE, null
            );

            // シフトパターンIDがnullで設定されること
            assertNull(record.getShiftPatternId(), "シフト未割当の場合はnullであること");
            assertEquals(AttendanceStatus.NOT_CLOCKED, record.getStatus(), "ステータスがNOT_CLOCKEDであること");
        }
    }

    @Nested
    @DisplayName("reconstruct - DB復元ファクトリメソッド")
    class ReconstructTest {

        @Test
        @DisplayName("全フィールドを指定してDB復元できる")
        void shouldReconstructFromDatabaseFields() {
            // テスト用データ準備
            AttendanceRecordId id = AttendanceRecordId.generate();
            Instant now = Instant.now();
            ClockEntry clockInEntry = new ClockEntry(ClockType.CLOCK_IN, clockTimeOf(9), ClockSource.WEB);
            List<ClockEntry> entries = List.of(clockInEntry);
            WorkDuration duration = WorkDuration.of(480, 540, 60, 480);
            OvertimeDuration overtime = OvertimeDuration.of(60, 0, 0, 60);

            // DB復元
            AttendanceRecord record = AttendanceRecord.reconstruct(
                    id, TEST_EMPLOYEE_ID, TEST_WORK_DATE, TEST_SHIFT_PATTERN_ID,
                    AttendanceStatus.CLOCKED_OUT, entries, duration, overtime,
                    3, now, now
            );

            // 全フィールドが正しく復元されること
            assertEquals(id, record.getId(), "勤怠記録IDが復元されること");
            assertEquals(TEST_EMPLOYEE_ID, record.getEmployeeId(), "従業員IDが復元されること");
            assertEquals(TEST_WORK_DATE, record.getWorkDate(), "勤務日が復元されること");
            assertEquals(TEST_SHIFT_PATTERN_ID, record.getShiftPatternId(), "シフトパターンIDが復元されること");
            assertEquals(AttendanceStatus.CLOCKED_OUT, record.getStatus(), "ステータスが復元されること");
            assertEquals(1, record.getClockEntries().size(), "打刻エントリが復元されること");
            assertEquals(duration, record.getWorkDuration(), "勤務時間が復元されること");
            assertEquals(overtime, record.getOvertimeDuration(), "残業時間が復元されること");
            assertEquals(3, record.getVersion(), "バージョンが復元されること");
            assertEquals(now, record.getCreatedAt(), "作成日時が復元されること");
            assertEquals(now, record.getUpdatedAt(), "更新日時が復元されること");
        }
    }

    // ========================
    // コマンドメソッドのテスト（7つ）
    // ========================

    @Nested
    @DisplayName("clockIn - 出勤打刻")
    class ClockInTest {

        @Test
        @DisplayName("NOT_CLOCKED状態から出勤打刻が成功する")
        void shouldClockInFromNotClocked() {
            // NOT_CLOCKED状態の勤怠記録を作成
            AttendanceRecord record = createNewRecord();
            ClockTime clockInTime = clockTimeOf(9); // 09:00

            // 出勤打刻を実行
            record.clockIn(clockInTime, ClockSource.WEB);

            // 状態遷移の検証: NOT_CLOCKED → CLOCKED_IN
            assertEquals(AttendanceStatus.CLOCKED_IN, record.getStatus(),
                    "ステータスがCLOCKED_INに遷移すること");

            // 打刻エントリの検証
            assertEquals(1, record.getClockEntries().size(), "打刻エントリが1件追加されること");
            ClockEntry entry = record.getClockEntries().get(0);
            assertEquals(ClockType.CLOCK_IN, entry.type(), "打刻種別がCLOCK_INであること");
            assertEquals(clockInTime, entry.time(), "打刻時刻が正しいこと");
            assertEquals(ClockSource.WEB, entry.source(), "打刻元がWEBであること");
        }

        @Test
        @DisplayName("MOBILE打刻元で出勤打刻が成功する")
        void shouldClockInWithMobileSource() {
            // MOBILE打刻元での出勤
            AttendanceRecord record = createNewRecord();
            record.clockIn(clockTimeOf(9), ClockSource.MOBILE);

            // 打刻元がMOBILEで記録されること
            assertEquals(ClockSource.MOBILE, record.getClockEntries().get(0).source(),
                    "打刻元がMOBILEであること");
        }

        @Test
        @DisplayName("CLOCKED_IN状態から出勤打刻するとIllegalStateExceptionが発生する")
        void shouldThrowWhenClockInFromClockedIn() {
            // 既にCLOCKED_IN状態の勤怠記録
            AttendanceRecord record = createClockedInRecord();

            // 二重出勤打刻 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.clockIn(clockTimeOf(10), TEST_SOURCE),
                    "CLOCKED_IN状態から出勤打刻すると例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("NOT_CLOCKED"),
                    "エラーメッセージにNOT_CLOCKEDが含まれること");
        }

        @Test
        @DisplayName("CLOCKED_OUT状態から出勤打刻するとIllegalStateExceptionが発生する")
        void shouldThrowWhenClockInFromClockedOut() {
            // 退勤済み状態の勤怠記録
            AttendanceRecord record = createClockedOutRecord();

            // 退勤後に出勤打刻 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.clockIn(clockTimeOf(10), TEST_SOURCE),
                    "CLOCKED_OUT状態から出勤打刻すると例外が発生すること"
            );
        }

        @Test
        @DisplayName("FINALIZED状態から出勤打刻するとIllegalStateExceptionが発生する")
        void shouldThrowWhenClockInFromFinalized() {
            // 確定済み状態の勤怠記録
            AttendanceRecord record = createFinalizedRecord();

            // 確定後に出勤打刻 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.clockIn(clockTimeOf(10), TEST_SOURCE),
                    "FINALIZED状態から出勤打刻すると例外が発生すること"
            );
        }
    }

    @Nested
    @DisplayName("clockOut - 退勤打刻")
    class ClockOutTest {

        @Test
        @DisplayName("CLOCKED_IN状態から退勤打刻が成功する")
        void shouldClockOutFromClockedIn() {
            // 出勤中の勤怠記録を作成
            AttendanceRecord record = createClockedInRecord();
            ClockTime clockOutTime = clockTimeOf(18); // 18:00

            // 退勤打刻を実行
            record.clockOut(clockOutTime, ClockSource.WEB);

            // 状態遷移の検証: CLOCKED_IN → CLOCKED_OUT
            assertEquals(AttendanceStatus.CLOCKED_OUT, record.getStatus(),
                    "ステータスがCLOCKED_OUTに遷移すること");

            // 打刻エントリの検証（出勤+退勤の2件）
            assertEquals(2, record.getClockEntries().size(), "打刻エントリが2件になること");
            ClockEntry clockOutEntry = record.getClockEntries().get(1);
            assertEquals(ClockType.CLOCK_OUT, clockOutEntry.type(), "打刻種別がCLOCK_OUTであること");
            assertEquals(clockOutTime, clockOutEntry.time(), "打刻時刻が正しいこと");
            assertEquals(ClockSource.WEB, clockOutEntry.source(), "打刻元がWEBであること");
        }

        @Test
        @DisplayName("退勤時刻が出勤時刻より前の場合にIllegalStateExceptionが発生する（INV-ATT-001）")
        void shouldThrowWhenClockOutBeforeClockIn() {
            // 出勤中の勤怠記録（09:00出勤）
            AttendanceRecord record = createClockedInRecord();

            // 出勤より前の時刻（08:00）で退勤 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.clockOut(clockTimeOf(8), TEST_SOURCE),
                    "退勤時刻が出勤時刻より前の場合に例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("INV-ATT-001"),
                    "エラーメッセージにINV-ATT-001が含まれること");
        }

        @Test
        @DisplayName("退勤時刻が出勤時刻と同じ場合にIllegalStateExceptionが発生する（INV-ATT-001）")
        void shouldThrowWhenClockOutEqualsClockIn() {
            // 出勤中の勤怠記録（09:00出勤）
            AttendanceRecord record = createClockedInRecord();

            // 出勤と同じ時刻（09:00）で退勤 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.clockOut(clockTimeOf(9), TEST_SOURCE),
                    "退勤時刻が出勤時刻と同じ場合に例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("INV-ATT-001"),
                    "エラーメッセージにINV-ATT-001が含まれること");
        }

        @Test
        @DisplayName("NOT_CLOCKED状態から退勤打刻するとIllegalStateExceptionが発生する")
        void shouldThrowWhenClockOutFromNotClocked() {
            // 未打刻状態の勤怠記録
            AttendanceRecord record = createNewRecord();

            // 出勤していないのに退勤 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.clockOut(clockTimeOf(18), TEST_SOURCE),
                    "NOT_CLOCKED状態から退勤打刻すると例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("CLOCKED_IN"),
                    "エラーメッセージにCLOCKED_INが含まれること");
        }

        @Test
        @DisplayName("CLOCKED_OUT状態から退勤打刻するとIllegalStateExceptionが発生する")
        void shouldThrowWhenClockOutFromClockedOut() {
            // 既に退勤済み状態の勤怠記録
            AttendanceRecord record = createClockedOutRecord();

            // 二重退勤打刻 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.clockOut(clockTimeOf(19), TEST_SOURCE),
                    "CLOCKED_OUT状態から退勤打刻すると例外が発生すること"
            );
        }

        @Test
        @DisplayName("FINALIZED状態から退勤打刻するとIllegalStateExceptionが発生する")
        void shouldThrowWhenClockOutFromFinalized() {
            // 確定済み状態の勤怠記録
            AttendanceRecord record = createFinalizedRecord();

            // 確定後に退勤打刻 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.clockOut(clockTimeOf(18), TEST_SOURCE),
                    "FINALIZED状態から退勤打刻すると例外が発生すること"
            );
        }
    }

    @Nested
    @DisplayName("startBreak - 休憩開始")
    class StartBreakTest {

        @Test
        @DisplayName("CLOCKED_IN状態から休憩開始が成功する")
        void shouldStartBreakFromClockedIn() {
            // 出勤中の勤怠記録を作成
            AttendanceRecord record = createClockedInRecord();
            ClockTime breakStartTime = clockTimeOf(12); // 12:00

            // 休憩開始を実行
            record.startBreak(breakStartTime, ClockSource.WEB);

            // ステータスはCLOCKED_INのまま（休憩中でもステータスは変わらない）
            assertEquals(AttendanceStatus.CLOCKED_IN, record.getStatus(),
                    "ステータスがCLOCKED_INのままであること");

            // 打刻エントリの検証（出勤+休憩開始の2件）
            assertEquals(2, record.getClockEntries().size(), "打刻エントリが2件になること");
            ClockEntry breakEntry = record.getClockEntries().get(1);
            assertEquals(ClockType.BREAK_START, breakEntry.type(), "打刻種別がBREAK_STARTであること");
            assertEquals(breakStartTime, breakEntry.time(), "打刻時刻が正しいこと");

            // 休憩中フラグの検証
            assertTrue(record.isOnBreak(), "休憩中であること");
        }

        @Test
        @DisplayName("休憩開始時刻が出勤時刻より前の場合にIllegalStateExceptionが発生する（INV-ATT-002）")
        void shouldThrowWhenStartBreakBeforeClockIn() {
            // 出勤中の勤怠記録（09:00出勤）
            AttendanceRecord record = createClockedInRecord();

            // 出勤より前の時刻（08:00）で休憩開始 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.startBreak(clockTimeOf(8), TEST_SOURCE),
                    "休憩開始時刻が出勤時刻より前の場合に例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("INV-ATT-002"),
                    "エラーメッセージにINV-ATT-002が含まれること");
        }

        @Test
        @DisplayName("休憩開始時刻が出勤時刻と同じ場合にIllegalStateExceptionが発生する（INV-ATT-002）")
        void shouldThrowWhenStartBreakEqualsClockIn() {
            // 出勤中の勤怠記録（09:00出勤）
            AttendanceRecord record = createClockedInRecord();

            // 出勤と同じ時刻（09:00）で休憩開始 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.startBreak(clockTimeOf(9), TEST_SOURCE),
                    "休憩開始時刻が出勤時刻と同じ場合に例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("INV-ATT-002"),
                    "エラーメッセージにINV-ATT-002が含まれること");
        }

        @Test
        @DisplayName("NOT_CLOCKED状態から休憩開始するとIllegalStateExceptionが発生する")
        void shouldThrowWhenStartBreakFromNotClocked() {
            // 未打刻状態の勤怠記録
            AttendanceRecord record = createNewRecord();

            // 出勤していないのに休憩 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.startBreak(clockTimeOf(12), TEST_SOURCE),
                    "NOT_CLOCKED状態から休憩開始すると例外が発生すること"
            );
        }

        @Test
        @DisplayName("CLOCKED_OUT状態から休憩開始するとIllegalStateExceptionが発生する")
        void shouldThrowWhenStartBreakFromClockedOut() {
            // 退勤済み状態の勤怠記録
            AttendanceRecord record = createClockedOutRecord();

            // 退勤後に休憩 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.startBreak(clockTimeOf(12), TEST_SOURCE),
                    "CLOCKED_OUT状態から休憩開始すると例外が発生すること"
            );
        }

        @Test
        @DisplayName("FINALIZED状態から休憩開始するとIllegalStateExceptionが発生する")
        void shouldThrowWhenStartBreakFromFinalized() {
            // 確定済み状態の勤怠記録
            AttendanceRecord record = createFinalizedRecord();

            // 確定後に休憩 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.startBreak(clockTimeOf(12), TEST_SOURCE),
                    "FINALIZED状態から休憩開始すると例外が発生すること"
            );
        }

        @Test
        @DisplayName("すでに休憩中の場合に休憩開始するとIllegalStateExceptionが発生する")
        void shouldThrowWhenAlreadyOnBreak() {
            // 休憩中の勤怠記録を作成
            AttendanceRecord record = createClockedInRecord();
            record.startBreak(clockTimeOf(12), TEST_SOURCE); // 休憩開始済み

            // 二重休憩開始 → 例外発生（INV-ATT-005）
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.startBreak(clockTimeOf(13), TEST_SOURCE),
                    "すでに休憩中に休憩開始すると例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("すでに休憩中"),
                    "エラーメッセージに休憩中の旨が含まれること");
        }
    }

    @Nested
    @DisplayName("endBreak - 休憩終了")
    class EndBreakTest {

        @Test
        @DisplayName("休憩中の状態から休憩終了が成功する")
        void shouldEndBreakWhenOnBreak() {
            // 休憩中の勤怠記録を作成
            AttendanceRecord record = createClockedInRecord();
            record.startBreak(clockTimeOf(12), TEST_SOURCE); // 12:00 休憩開始
            ClockTime breakEndTime = clockTimeOf(13); // 13:00

            // 休憩終了を実行
            record.endBreak(breakEndTime, ClockSource.WEB);

            // ステータスはCLOCKED_INのまま
            assertEquals(AttendanceStatus.CLOCKED_IN, record.getStatus(),
                    "ステータスがCLOCKED_INのままであること");

            // 打刻エントリの検証（出勤+休憩開始+休憩終了の3件）
            assertEquals(3, record.getClockEntries().size(), "打刻エントリが3件になること");
            ClockEntry breakEndEntry = record.getClockEntries().get(2);
            assertEquals(ClockType.BREAK_END, breakEndEntry.type(), "打刻種別がBREAK_ENDであること");
            assertEquals(breakEndTime, breakEndEntry.time(), "打刻時刻が正しいこと");

            // 休憩中フラグの検証
            assertFalse(record.isOnBreak(), "休憩中でないこと");
        }

        @Test
        @DisplayName("休憩終了時刻が休憩開始時刻より前の場合にIllegalStateExceptionが発生する（INV-ATT-002）")
        void shouldThrowWhenEndBreakBeforeStartBreak() {
            // 出勤中かつ休憩中の勤怠記録（09:00出勤、12:00休憩開始）
            AttendanceRecord record = createClockedInRecord();
            record.startBreak(clockTimeOf(12), TEST_SOURCE);

            // 休憩開始より前の時刻（11:00）で休憩終了 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.endBreak(clockTimeOf(11), TEST_SOURCE),
                    "休憩終了時刻が休憩開始時刻より前の場合に例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("INV-ATT-002"),
                    "エラーメッセージにINV-ATT-002が含まれること");
        }

        @Test
        @DisplayName("休憩終了時刻が休憩開始時刻と同じ場合にIllegalStateExceptionが発生する（INV-ATT-002）")
        void shouldThrowWhenEndBreakEqualsStartBreak() {
            // 出勤中かつ休憩中の勤怠記録（09:00出勤、12:00休憩開始）
            AttendanceRecord record = createClockedInRecord();
            record.startBreak(clockTimeOf(12), TEST_SOURCE);

            // 休憩開始と同じ時刻（12:00）で休憩終了 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.endBreak(clockTimeOf(12), TEST_SOURCE),
                    "休憩終了時刻が休憩開始時刻と同じ場合に例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("INV-ATT-002"),
                    "エラーメッセージにINV-ATT-002が含まれること");
        }

        @Test
        @DisplayName("NOT_CLOCKED状態から休憩終了するとIllegalStateExceptionが発生する")
        void shouldThrowWhenEndBreakFromNotClocked() {
            // 未打刻状態の勤怠記録
            AttendanceRecord record = createNewRecord();

            // 出勤していないのに休憩終了 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.endBreak(clockTimeOf(13), TEST_SOURCE),
                    "NOT_CLOCKED状態から休憩終了すると例外が発生すること"
            );
        }

        @Test
        @DisplayName("CLOCKED_OUT状態から休憩終了するとIllegalStateExceptionが発生する")
        void shouldThrowWhenEndBreakFromClockedOut() {
            // 退勤済み状態の勤怠記録
            AttendanceRecord record = createClockedOutRecord();

            // 退勤後に休憩終了 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.endBreak(clockTimeOf(13), TEST_SOURCE),
                    "CLOCKED_OUT状態から休憩終了すると例外が発生すること"
            );
        }

        @Test
        @DisplayName("FINALIZED状態から休憩終了するとIllegalStateExceptionが発生する")
        void shouldThrowWhenEndBreakFromFinalized() {
            // 確定済み状態の勤怠記録
            AttendanceRecord record = createFinalizedRecord();

            // 確定後に休憩終了 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.endBreak(clockTimeOf(13), TEST_SOURCE),
                    "FINALIZED状態から休憩終了すると例外が発生すること"
            );
        }

        @Test
        @DisplayName("休憩中でない場合に休憩終了するとIllegalStateExceptionが発生する")
        void shouldThrowWhenNotOnBreak() {
            // 出勤中だが休憩開始していない勤怠記録
            AttendanceRecord record = createClockedInRecord();

            // 休憩開始していないのに休憩終了 → 例外発生（INV-ATT-005）
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.endBreak(clockTimeOf(13), TEST_SOURCE),
                    "休憩中でないのに休憩終了すると例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("休憩中ではありません"),
                    "エラーメッセージに休憩中でない旨が含まれること");
        }

        @Test
        @DisplayName("休憩終了済み（休憩中でない）の場合に再度休憩終了するとIllegalStateExceptionが発生する")
        void shouldThrowWhenBreakAlreadyEnded() {
            // 休憩を完了した勤怠記録
            AttendanceRecord record = createClockedInRecord();
            record.startBreak(clockTimeOf(12), TEST_SOURCE);
            record.endBreak(clockTimeOf(13), TEST_SOURCE); // 休憩終了済み

            // 二重休憩終了 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.endBreak(clockTimeOf(14), TEST_SOURCE),
                    "休憩終了済みに再度休憩終了すると例外が発生すること"
            );
        }
    }

    @Nested
    @DisplayName("correctClock - 打刻修正")
    class CorrectClockTest {

        @Test
        @DisplayName("NOT_CLOCKED状態から打刻修正するとIllegalStateExceptionが発生する")
        void shouldThrowWhenCorrectClockFromNotClocked() {
            // 未打刻状態の勤怠記録（設計書: CLOCKED_OUTでのみ修正可能）
            AttendanceRecord record = createNewRecord();
            ClockCorrection correction = new ClockCorrection(
                    ClockType.CLOCK_IN,
                    clockTimeOf(9),
                    "打刻漏れのため修正",
                    testApprovalId()
            );

            // NOT_CLOCKED状態での修正 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.correctClock(correction),
                    "NOT_CLOCKED状態から打刻修正すると例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("CLOCKED_OUT"),
                    "エラーメッセージにCLOCKED_OUTが含まれること");
        }

        @Test
        @DisplayName("CLOCKED_IN状態から打刻修正するとIllegalStateExceptionが発生する")
        void shouldThrowWhenCorrectClockFromClockedIn() {
            // 出勤中の勤怠記録（設計書: CLOCKED_OUTでのみ修正可能）
            AttendanceRecord record = createClockedInRecord();
            ClockCorrection correction = new ClockCorrection(
                    ClockType.CLOCK_IN,
                    clockTimeOf(8),
                    "出勤時刻の誤りを修正",
                    testApprovalId()
            );

            // CLOCKED_IN状態での修正 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.correctClock(correction),
                    "CLOCKED_IN状態から打刻修正すると例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("CLOCKED_OUT"),
                    "エラーメッセージにCLOCKED_OUTが含まれること");
        }

        @Test
        @DisplayName("CLOCKED_OUT状態で打刻修正が成功する")
        void shouldCorrectClockFromClockedOut() {
            // 退勤済み状態の勤怠記録（退勤時刻を修正するケース）
            AttendanceRecord record = createClockedOutRecord();
            ClockCorrection correction = new ClockCorrection(
                    ClockType.CLOCK_OUT,
                    clockTimeOf(19), // 18:00 → 19:00 に修正
                    "退勤時刻の誤りを修正",
                    testApprovalId()
            );

            // 打刻修正を実行
            record.correctClock(correction);

            // 出勤 + 退勤 + 修正の3件
            assertEquals(3, record.getClockEntries().size(), "修正エントリが追加されること");
        }

        @Test
        @DisplayName("FINALIZED状態から打刻修正するとIllegalStateExceptionが発生する")
        void shouldThrowWhenCorrectClockFromFinalized() {
            // 確定済み状態の勤怠記録（INV-ATT-003: FINALIZED後は変更不可）
            AttendanceRecord record = createFinalizedRecord();
            ClockCorrection correction = new ClockCorrection(
                    ClockType.CLOCK_IN,
                    clockTimeOf(8),
                    "修正したい",
                    testApprovalId()
            );

            // 確定後の修正 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.correctClock(correction),
                    "FINALIZED状態から打刻修正すると例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("CLOCKED_OUT"),
                    "エラーメッセージにCLOCKED_OUTが含まれること");
        }
    }

    @Nested
    @DisplayName("registerManualAttendance - 勤務実績の手動登録")
    class RegisterManualAttendanceTest {

        @Test
        @DisplayName("NOT_CLOCKED状態から勤務実績の手動登録が成功する")
        void shouldRegisterManualAttendanceFromNotClocked() {
            // 未打刻状態の勤怠記録
            AttendanceRecord record = createNewRecord();
            ClockTime startTime = clockTimeOf(9);  // 09:00
            ClockTime endTime = clockTimeOf(18);    // 18:00
            ManualAttendance manual = new ManualAttendance(
                    startTime, endTime, "通常勤務", "打刻漏れのため", testApprovalId()
            );

            // 勤務実績の手動登録を実行
            record.registerManualAttendance(manual);

            // 状態遷移の検証: NOT_CLOCKED → CLOCKED_OUT（一気に退勤済みになる）
            assertEquals(AttendanceStatus.CLOCKED_OUT, record.getStatus(),
                    "ステータスがCLOCKED_OUTに遷移すること（出勤→退勤を一度に行う）");

            // 打刻エントリの検証（出勤+退勤の2件がMANUALソースで追加される）
            assertEquals(2, record.getClockEntries().size(), "出勤・退勤の2エントリが追加されること");

            // 出勤エントリの検証
            ClockEntry clockInEntry = record.getClockEntries().get(0);
            assertEquals(ClockType.CLOCK_IN, clockInEntry.type(), "1件目がCLOCK_INであること");
            assertEquals(startTime, clockInEntry.time(), "出勤時刻が正しいこと");
            assertEquals(ClockSource.MANUAL, clockInEntry.source(), "打刻元がMANUALであること");

            // 退勤エントリの検証
            ClockEntry clockOutEntry = record.getClockEntries().get(1);
            assertEquals(ClockType.CLOCK_OUT, clockOutEntry.type(), "2件目がCLOCK_OUTであること");
            assertEquals(endTime, clockOutEntry.time(), "退勤時刻が正しいこと");
            assertEquals(ClockSource.MANUAL, clockOutEntry.source(), "打刻元がMANUALであること");
        }

        @Test
        @DisplayName("終了時刻が開始時刻より前の場合にIllegalStateExceptionが発生する（INV-ATT-001）")
        void shouldThrowWhenManualEndTimeBeforeStartTime() {
            // 未打刻状態の勤怠記録
            AttendanceRecord record = createNewRecord();
            ManualAttendance manual = new ManualAttendance(
                    clockTimeOf(18), clockTimeOf(9), "通常勤務", "理由", testApprovalId()
            );

            // 終了時刻（09:00）が開始時刻（18:00）より前 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.registerManualAttendance(manual),
                    "終了時刻が開始時刻より前の場合に例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("INV-ATT-001"),
                    "エラーメッセージにINV-ATT-001が含まれること");
        }

        @Test
        @DisplayName("終了時刻が開始時刻と同じ場合にIllegalStateExceptionが発生する（INV-ATT-001）")
        void shouldThrowWhenManualEndTimeEqualsStartTime() {
            // 未打刻状態の勤怠記録
            AttendanceRecord record = createNewRecord();
            ManualAttendance manual = new ManualAttendance(
                    clockTimeOf(9), clockTimeOf(9), "通常勤務", "理由", testApprovalId()
            );

            // 終了時刻と開始時刻が同じ → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.registerManualAttendance(manual),
                    "終了時刻が開始時刻と同じ場合に例外が発生すること"
            );
            assertTrue(exception.getMessage().contains("INV-ATT-001"),
                    "エラーメッセージにINV-ATT-001が含まれること");
        }

        @Test
        @DisplayName("CLOCKED_IN状態から勤務実績の手動登録するとIllegalStateExceptionが発生する")
        void shouldThrowWhenRegisterManualFromClockedIn() {
            // 出勤中の勤怠記録
            AttendanceRecord record = createClockedInRecord();
            ManualAttendance manual = new ManualAttendance(
                    clockTimeOf(9), clockTimeOf(18), "通常勤務", "理由", testApprovalId()
            );

            // 出勤済みの状態で手動登録 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.registerManualAttendance(manual),
                    "CLOCKED_IN状態から勤務実績を手動登録すると例外が発生すること"
            );
        }

        @Test
        @DisplayName("CLOCKED_OUT状態から勤務実績の手動登録するとIllegalStateExceptionが発生する")
        void shouldThrowWhenRegisterManualFromClockedOut() {
            // 退勤済みの勤怠記録
            AttendanceRecord record = createClockedOutRecord();
            ManualAttendance manual = new ManualAttendance(
                    clockTimeOf(9), clockTimeOf(18), "通常勤務", "理由", testApprovalId()
            );

            // 退勤済みの状態で手動登録 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.registerManualAttendance(manual),
                    "CLOCKED_OUT状態から勤務実績を手動登録すると例外が発生すること"
            );
        }

        @Test
        @DisplayName("FINALIZED状態から勤務実績の手動登録するとIllegalStateExceptionが発生する")
        void shouldThrowWhenRegisterManualFromFinalized() {
            // 確定済み状態の勤怠記録
            AttendanceRecord record = createFinalizedRecord();
            ManualAttendance manual = new ManualAttendance(
                    clockTimeOf(9), clockTimeOf(18), "通常勤務", "理由", testApprovalId()
            );

            // 確定後に手動登録 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.registerManualAttendance(manual),
                    "FINALIZED状態から勤務実績を手動登録すると例外が発生すること"
            );
        }
    }

    @Nested
    @DisplayName("finalizeRecord - 本締め確定")
    class FinalizeRecordTest {

        @Test
        @DisplayName("CLOCKED_OUT状態から本締め確定が成功する")
        void shouldFinalizeFromClockedOut() {
            // 退勤済み状態の勤怠記録
            AttendanceRecord record = createClockedOutRecord();
            String monthlyClosingId = "monthly-closing-202602";

            // 本締め確定を実行
            record.finalizeRecord(monthlyClosingId);

            // 状態遷移の検証: CLOCKED_OUT → FINALIZED
            assertEquals(AttendanceStatus.FINALIZED, record.getStatus(),
                    "ステータスがFINALIZEDに遷移すること");
        }

        @Test
        @DisplayName("NOT_CLOCKED状態から本締め確定するとIllegalStateExceptionが発生する")
        void shouldThrowWhenFinalizeFromNotClocked() {
            // 未打刻状態の勤怠記録
            AttendanceRecord record = createNewRecord();

            // 出勤すらしていないのに確定 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.finalizeRecord("monthly-closing-001"),
                    "NOT_CLOCKED状態から本締め確定すると例外が発生すること"
            );
        }

        @Test
        @DisplayName("CLOCKED_IN状態から本締め確定するとIllegalStateExceptionが発生する")
        void shouldThrowWhenFinalizeFromClockedIn() {
            // 出勤中の勤怠記録（退勤していない）
            AttendanceRecord record = createClockedInRecord();

            // 退勤していないのに確定 → 例外発生
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> record.finalizeRecord("monthly-closing-001"),
                    "CLOCKED_IN状態から本締め確定すると例外が発生すること（退勤が先に必要）"
            );
            assertTrue(exception.getMessage().contains("CLOCKED_OUT"),
                    "エラーメッセージにCLOCKED_OUTが含まれること");
        }

        @Test
        @DisplayName("FINALIZED状態から再度本締め確定するとIllegalStateExceptionが発生する")
        void shouldThrowWhenFinalizeFromFinalized() {
            // 既に確定済みの勤怠記録
            AttendanceRecord record = createFinalizedRecord();

            // 二重確定 → 例外発生
            assertThrows(
                    IllegalStateException.class,
                    () -> record.finalizeRecord("monthly-closing-002"),
                    "FINALIZED状態から再度本締め確定すると例外が発生すること"
            );
        }
    }

    // ========================
    // 正常フロー（シナリオテスト）
    // ========================

    @Nested
    @DisplayName("正常フロー - 一連の勤務シナリオ")
    class NormalFlowTest {

        @Test
        @DisplayName("出勤 → 退勤 の基本フローが成功する")
        void shouldCompleteBasicClockInOutFlow() {
            // 新規勤怠記録を作成
            AttendanceRecord record = createNewRecord();
            assertEquals(AttendanceStatus.NOT_CLOCKED, record.getStatus(), "初期状態がNOT_CLOCKEDであること");

            // Step 1: 出勤打刻
            record.clockIn(clockTimeOf(9), ClockSource.WEB);
            assertEquals(AttendanceStatus.CLOCKED_IN, record.getStatus(), "出勤後にCLOCKED_INであること");

            // Step 2: 退勤打刻
            record.clockOut(clockTimeOf(18), ClockSource.WEB);
            assertEquals(AttendanceStatus.CLOCKED_OUT, record.getStatus(), "退勤後にCLOCKED_OUTであること");

            // 打刻エントリの検証
            assertEquals(2, record.getClockEntries().size(), "出勤+退勤で2件のエントリがあること");
        }

        @Test
        @DisplayName("出勤 → 休憩開始 → 休憩終了 → 退勤 の休憩ありフローが成功する")
        void shouldCompleteFlowWithBreak() {
            // 新規勤怠記録を作成
            AttendanceRecord record = createNewRecord();

            // Step 1: 09:00 出勤
            record.clockIn(clockTimeOf(9), ClockSource.WEB);
            assertEquals(AttendanceStatus.CLOCKED_IN, record.getStatus());
            assertFalse(record.isOnBreak(), "出勤直後は休憩中でないこと");

            // Step 2: 12:00 休憩開始
            record.startBreak(clockTimeOf(12), ClockSource.WEB);
            assertEquals(AttendanceStatus.CLOCKED_IN, record.getStatus(), "休憩中もCLOCKED_INのままであること");
            assertTrue(record.isOnBreak(), "休憩開始後は休憩中であること");

            // Step 3: 13:00 休憩終了
            record.endBreak(clockTimeOf(13), ClockSource.WEB);
            assertEquals(AttendanceStatus.CLOCKED_IN, record.getStatus(), "休憩終了後もCLOCKED_INであること");
            assertFalse(record.isOnBreak(), "休憩終了後は休憩中でないこと");

            // Step 4: 18:00 退勤
            record.clockOut(clockTimeOf(18), ClockSource.WEB);
            assertEquals(AttendanceStatus.CLOCKED_OUT, record.getStatus(), "退勤後にCLOCKED_OUTであること");

            // 打刻エントリの検証（出勤+休憩開始+休憩終了+退勤の4件）
            assertEquals(4, record.getClockEntries().size(), "4件のエントリがあること");
            assertEquals(ClockType.CLOCK_IN, record.getClockEntries().get(0).type());
            assertEquals(ClockType.BREAK_START, record.getClockEntries().get(1).type());
            assertEquals(ClockType.BREAK_END, record.getClockEntries().get(2).type());
            assertEquals(ClockType.CLOCK_OUT, record.getClockEntries().get(3).type());
        }

        @Test
        @DisplayName("出勤 → 退勤 → 本締め確定 の完全フローが成功する")
        void shouldCompleteFullFlowWithFinalize() {
            // 新規勤怠記録を作成
            AttendanceRecord record = createNewRecord();

            // 状態遷移: NOT_CLOCKED → CLOCKED_IN → CLOCKED_OUT → FINALIZED
            record.clockIn(clockTimeOf(9), ClockSource.WEB);
            record.clockOut(clockTimeOf(18), ClockSource.WEB);
            record.finalizeRecord("monthly-closing-202602");

            // 最終状態がFINALIZEDであること
            assertEquals(AttendanceStatus.FINALIZED, record.getStatus(),
                    "完全フロー後にFINALIZEDであること");
        }

        @Test
        @DisplayName("複数回の休憩サイクルが正しく動作する")
        void shouldHandleMultipleBreakCycles() {
            // 新規勤怠記録を作成
            AttendanceRecord record = createNewRecord();
            record.clockIn(clockTimeOf(9), ClockSource.WEB);

            // 第1回休憩: 12:00-12:30
            record.startBreak(clockTimeOf(12), ClockSource.WEB);
            assertTrue(record.isOnBreak(), "第1回休憩中であること");
            record.endBreak(new ClockTime(Instant.parse("2026-02-23T12:30:00Z")), ClockSource.WEB);
            assertFalse(record.isOnBreak(), "第1回休憩終了後は休憩中でないこと");

            // 第2回休憩: 15:00-15:15
            record.startBreak(clockTimeOf(15), ClockSource.WEB);
            assertTrue(record.isOnBreak(), "第2回休憩中であること");
            record.endBreak(new ClockTime(Instant.parse("2026-02-23T15:15:00Z")), ClockSource.WEB);
            assertFalse(record.isOnBreak(), "第2回休憩終了後は休憩中でないこと");

            // 第3回休憩: 17:00-17:10
            record.startBreak(clockTimeOf(17), ClockSource.WEB);
            assertTrue(record.isOnBreak(), "第3回休憩中であること");
            record.endBreak(new ClockTime(Instant.parse("2026-02-23T17:10:00Z")), ClockSource.WEB);
            assertFalse(record.isOnBreak(), "第3回休憩終了後は休憩中でないこと");

            // 退勤
            record.clockOut(clockTimeOf(18), ClockSource.WEB);

            // 打刻エントリの検証: 出勤 + (休憩開始+休憩終了) x 3 + 退勤 = 8件
            assertEquals(8, record.getClockEntries().size(), "8件のエントリがあること");
            assertEquals(AttendanceStatus.CLOCKED_OUT, record.getStatus(),
                    "複数休憩後の退勤でCLOCKED_OUTであること");
        }

        @Test
        @DisplayName("手動登録 → 本締め確定 のフローが成功する")
        void shouldCompleteManualAttendanceAndFinalizeFlow() {
            // 新規勤怠記録を作成
            AttendanceRecord record = createNewRecord();
            ManualAttendance manual = new ManualAttendance(
                    clockTimeOf(9), clockTimeOf(18), "通常勤務", "打刻漏れ", testApprovalId()
            );

            // 手動登録 → CLOCKED_OUT
            record.registerManualAttendance(manual);
            assertEquals(AttendanceStatus.CLOCKED_OUT, record.getStatus(),
                    "手動登録後にCLOCKED_OUTであること");

            // 本締め確定 → FINALIZED
            record.finalizeRecord("monthly-closing-202602");
            assertEquals(AttendanceStatus.FINALIZED, record.getStatus(),
                    "本締め確定後にFINALIZEDであること");
        }
    }

    // ========================
    // FINALIZED状態からの全コマンド拒否テスト
    // ========================

    @Nested
    @DisplayName("FINALIZED状態 - 全コマンドが拒否される（INV-ATT-003）")
    class FinalizedGuardTest {

        /** 確定済みの勤怠記録 */
        private AttendanceRecord finalizedRecord;

        @BeforeEach
        void setUp() {
            // FINALIZED状態の勤怠記録を用意
            finalizedRecord = createFinalizedRecord();
        }

        @Test
        @DisplayName("FINALIZED後にclockInが拒否される")
        void shouldRejectClockInAfterFinalized() {
            assertThrows(IllegalStateException.class,
                    () -> finalizedRecord.clockIn(clockTimeOf(9), TEST_SOURCE),
                    "FINALIZED後のclockInは拒否されること");
        }

        @Test
        @DisplayName("FINALIZED後にclockOutが拒否される")
        void shouldRejectClockOutAfterFinalized() {
            assertThrows(IllegalStateException.class,
                    () -> finalizedRecord.clockOut(clockTimeOf(18), TEST_SOURCE),
                    "FINALIZED後のclockOutは拒否されること");
        }

        @Test
        @DisplayName("FINALIZED後にstartBreakが拒否される")
        void shouldRejectStartBreakAfterFinalized() {
            assertThrows(IllegalStateException.class,
                    () -> finalizedRecord.startBreak(clockTimeOf(12), TEST_SOURCE),
                    "FINALIZED後のstartBreakは拒否されること");
        }

        @Test
        @DisplayName("FINALIZED後にendBreakが拒否される")
        void shouldRejectEndBreakAfterFinalized() {
            assertThrows(IllegalStateException.class,
                    () -> finalizedRecord.endBreak(clockTimeOf(13), TEST_SOURCE),
                    "FINALIZED後のendBreakは拒否されること");
        }

        @Test
        @DisplayName("FINALIZED後にcorrectClockが拒否される")
        void shouldRejectCorrectClockAfterFinalized() {
            ClockCorrection correction = new ClockCorrection(
                    ClockType.CLOCK_IN, clockTimeOf(8), "修正理由", testApprovalId()
            );
            assertThrows(IllegalStateException.class,
                    () -> finalizedRecord.correctClock(correction),
                    "FINALIZED後のcorrectClockは拒否されること");
        }

        @Test
        @DisplayName("FINALIZED後にregisterManualAttendanceが拒否される")
        void shouldRejectRegisterManualAttendanceAfterFinalized() {
            ManualAttendance manual = new ManualAttendance(
                    clockTimeOf(9), clockTimeOf(18), "通常勤務", "理由", testApprovalId()
            );
            assertThrows(IllegalStateException.class,
                    () -> finalizedRecord.registerManualAttendance(manual),
                    "FINALIZED後のregisterManualAttendanceは拒否されること");
        }

        @Test
        @DisplayName("FINALIZED後にfinalizeRecordが拒否される")
        void shouldRejectFinalizeAfterFinalized() {
            assertThrows(IllegalStateException.class,
                    () -> finalizedRecord.finalizeRecord("monthly-closing-002"),
                    "FINALIZED後のfinalizeRecordは拒否されること");
        }
    }

    // ========================
    // 計算メソッドのテスト
    // ========================

    @Nested
    @DisplayName("calculateWorkDuration - 勤務時間設定")
    class CalculateWorkDurationTest {

        @Test
        @DisplayName("勤務時間と残業時間を設定できる")
        void shouldSetWorkDurationAndOvertime() {
            // 退勤済みの勤怠記録
            AttendanceRecord record = createClockedOutRecord();
            WorkDuration duration = WorkDuration.of(480, 540, 60, 480);
            OvertimeDuration overtime = OvertimeDuration.of(60, 0, 0, 60);

            // 勤務時間を設定
            record.calculateWorkDuration(duration, overtime);

            // 設定された値の検証
            assertEquals(duration, record.getWorkDuration(), "勤務時間が設定されること");
            assertEquals(overtime, record.getOvertimeDuration(), "残業時間が設定されること");
        }

        @Test
        @DisplayName("勤務時間にnullを設定するとNullPointerExceptionが発生する")
        void shouldThrowWhenDurationIsNull() {
            AttendanceRecord record = createClockedOutRecord();

            // nullの勤務時間 → 例外発生
            assertThrows(NullPointerException.class,
                    () -> record.calculateWorkDuration(null, OvertimeDuration.zero()),
                    "勤務時間にnullを設定すると例外が発生すること");
        }

        @Test
        @DisplayName("残業時間にnullを設定するとNullPointerExceptionが発生する")
        void shouldThrowWhenOvertimeIsNull() {
            AttendanceRecord record = createClockedOutRecord();

            // nullの残業時間 → 例外発生
            assertThrows(NullPointerException.class,
                    () -> record.calculateWorkDuration(WorkDuration.zero(), null),
                    "残業時間にnullを設定すると例外が発生すること");
        }
    }

    // ========================
    // isOnBreakヘルパーメソッドのテスト
    // ========================

    @Nested
    @DisplayName("isOnBreak - 休憩中判定")
    class IsOnBreakTest {

        @Test
        @DisplayName("打刻エントリが空の場合は休憩中でない")
        void shouldReturnFalseWhenNoEntries() {
            // 新規作成（打刻なし）
            AttendanceRecord record = createNewRecord();

            assertFalse(record.isOnBreak(), "打刻エントリなしの場合は休憩中でないこと");
        }

        @Test
        @DisplayName("出勤のみの場合は休憩中でない")
        void shouldReturnFalseWhenOnlyClockedIn() {
            // 出勤のみ
            AttendanceRecord record = createClockedInRecord();

            assertFalse(record.isOnBreak(), "出勤のみの場合は休憩中でないこと");
        }

        @Test
        @DisplayName("BREAK_STARTのみの場合は休憩中である")
        void shouldReturnTrueWhenBreakStartOnly() {
            // 休憩開始のみ（終了していない）
            AttendanceRecord record = createClockedInRecord();
            record.startBreak(clockTimeOf(12), TEST_SOURCE);

            assertTrue(record.isOnBreak(), "BREAK_START後は休憩中であること");
        }

        @Test
        @DisplayName("BREAK_START + BREAK_ENDのペアが揃っている場合は休憩中でない")
        void shouldReturnFalseWhenBreakPairComplete() {
            // 休憩開始 → 休憩終了（ペア完成）
            AttendanceRecord record = createClockedInRecord();
            record.startBreak(clockTimeOf(12), TEST_SOURCE);
            record.endBreak(clockTimeOf(13), TEST_SOURCE);

            assertFalse(record.isOnBreak(), "BREAK_START+BREAK_ENDのペアが揃えば休憩中でないこと");
        }

        @Test
        @DisplayName("複数休憩で最後のペアが未完了の場合は休憩中である")
        void shouldReturnTrueWhenLastBreakNotEnded() {
            // 第1回休憩（完了）+ 第2回休憩（開始のみ）
            AttendanceRecord record = createClockedInRecord();
            record.startBreak(clockTimeOf(12), TEST_SOURCE);
            record.endBreak(clockTimeOf(13), TEST_SOURCE);
            record.startBreak(clockTimeOf(15), TEST_SOURCE); // 2回目開始、未終了

            assertTrue(record.isOnBreak(), "最後の休憩が未終了の場合は休憩中であること");
        }
    }

    // ========================
    // 打刻エントリリストの不変性テスト
    // ========================

    @Nested
    @DisplayName("getClockEntries - 打刻エントリリストの不変性")
    class ClockEntriesImmutabilityTest {

        @Test
        @DisplayName("getClockEntriesで取得したリストは変更不可である")
        void shouldReturnUnmodifiableList() {
            // 出勤済みの勤怠記録
            AttendanceRecord record = createClockedInRecord();

            // 取得したリストに対する変更操作 → 例外発生
            assertThrows(UnsupportedOperationException.class,
                    () -> record.getClockEntries().add(
                            new ClockEntry(ClockType.CLOCK_OUT, clockTimeOf(18), ClockSource.WEB)
                    ),
                    "getClockEntriesの結果リストは変更不可であること"
            );
        }
    }
}
