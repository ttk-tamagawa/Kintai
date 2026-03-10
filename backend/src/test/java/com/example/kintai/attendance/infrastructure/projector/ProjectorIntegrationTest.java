package com.example.kintai.attendance.infrastructure.projector;

import com.example.kintai.attendance.application.command.AttendanceCommandService;
import com.example.kintai.attendance.application.command.ShiftPatternCommandService;
import com.example.kintai.attendance.application.command.WeeklyScheduleCommandService;
import com.example.kintai.attendance.domain.model.ClockSource;
import com.example.kintai.attendance.domain.model.ClockTime;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * プロジェクター統合テスト — イベント発行→Read Model更新の整合性を検証する
 *
 * <p>プロジェクターは {@code @TransactionalEventListener(phase = AFTER_COMMIT)} で動作するため、
 * テストメソッドに {@code @Transactional} を付けるとイベントが発火しない。
 * そのため、テストは非トランザクションで実行し、サービスの呼び出しで実際にコミットさせる。</p>
 *
 * <p>テスト対象:
 * <ul>
 *   <li>{@link AttendanceSummaryProjector} — 出勤打刻→attendance_summaries更新</li>
 *   <li>{@link WeeklyScheduleSummaryProjector} — シフト割当/公開→weekly_schedule_summaries更新</li>
 * </ul>
 * </p>
 *
 * <p>対応タスク: 9-2-4 プロジェクター統合テスト</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("プロジェクター統合テスト — イベント発行→Read Model更新")
class ProjectorIntegrationTest {

    /** タイムゾーン: Asia/Tokyo（打刻時刻→勤務日の変換に使用） */
    private static final ZoneId ZONE_TOKYO = ZoneId.of("Asia/Tokyo");

    @Autowired
    private AttendanceCommandService attendanceCommandService;

    @Autowired
    private ShiftPatternCommandService shiftPatternCommandService;

    @Autowired
    private WeeklyScheduleCommandService weeklyScheduleCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ========================================
    // 勤怠記録プロジェクターのテスト
    // ========================================

    @Nested
    @DisplayName("AttendanceSummaryProjector — 日次勤怠サマリー更新")
    class AttendanceSummaryProjectorTest {

        @BeforeEach
        void setUp() {
            // Read Model・イベントストア・打刻ログ・集約テーブルをクリーンアップする
            // FK制約の順序に従い、子テーブルから先に削除する
            jdbcTemplate.execute("DELETE FROM attendance_summaries");
            jdbcTemplate.execute("DELETE FROM monthly_attendance_summaries");
            jdbcTemplate.execute("DELETE FROM attendance_events");
            jdbcTemplate.execute("DELETE FROM clock_entries");
            jdbcTemplate.execute("DELETE FROM attendances");
        }

        @Test
        @DisplayName("出勤打刻 → attendance_summariesにCLOCKED_INステータスの行が作成される")
        void clockIn_createsAttendanceSummaryWithClockedInStatus() {
            // テスト用のユニークな従業員IDを生成する
            EmployeeId employeeId = EmployeeId.of(UUID.randomUUID());

            // 本日9:00（JST）の打刻時刻を作成する
            Instant clockInInstant = ZonedDateTime.now(ZONE_TOKYO)
                    .withHour(9).withMinute(0).withSecond(0).withNano(0)
                    .toInstant();
            ClockTime clockInTime = new ClockTime(clockInInstant);

            // 出勤打刻を実行する（サービス経由で集約保存+イベント発行+プロジェクター実行）
            AttendanceRecordId attendanceId = attendanceCommandService.clockIn(
                    employeeId, clockInTime, ClockSource.WEB, null
            );

            // attendance_summariesから該当行を取得して検証する
            Map<String, Object> summary = jdbcTemplate.queryForMap(
                    "SELECT * FROM attendance_summaries WHERE attendance_id = ?",
                    attendanceId.value()
            );

            // ステータスがCLOCKED_INであることを検証する
            assertEquals("CLOCKED_IN", summary.get("status"),
                    "出勤打刻後のステータスはCLOCKED_INであるべき");

            // 従業員IDが正しく設定されていることを検証する
            assertEquals(employeeId.value(), summary.get("employee_id"),
                    "従業員IDが正しく設定されているべき");

            // 出勤時刻が記録されていることを検証する
            assertNotNull(summary.get("clock_in_time"),
                    "出勤時刻が記録されているべき");

            // 退勤時刻はまだnullであることを検証する
            assertNull(summary.get("clock_out_time"),
                    "退勤前なので退勤時刻はnullであるべき");

            // イベント数が1であることを検証する（ClockedInEvent1件）
            assertEquals(1, summary.get("event_count"),
                    "出勤打刻後のイベント数は1であるべき");

            // 勤務時間は0のままであることを検証する（退勤前）
            assertEquals(0, summary.get("net_work_minutes"),
                    "退勤前の正味労働時間は0であるべき");
        }

        /**
         * 出勤打刻＋退勤打刻のEnd-to-Endテスト
         *
         * <p>V7マイグレーションでattendance_eventsのCHECK制約をコード側のイベント型名
         * （WORK_DURATION_CALCULATED, MANUAL_ATTENDANCE_REGISTERED, ATTENDANCE_FINALIZED）
         * に合わせて修正済み。</p>
         */
        @Test
        @DisplayName("出勤＋退勤打刻 → attendance_summariesに勤務時間が反映される")
        void clockInAndOut_updatesAttendanceSummaryWithWorkDuration() {
            // テスト用のユニークな従業員IDを生成する
            EmployeeId employeeId = EmployeeId.of(UUID.randomUUID());

            // 本日9:00（JST）に出勤する
            Instant clockInInstant = ZonedDateTime.now(ZONE_TOKYO)
                    .withHour(9).withMinute(0).withSecond(0).withNano(0)
                    .toInstant();
            ClockTime clockInTime = new ClockTime(clockInInstant);

            AttendanceRecordId attendanceId = attendanceCommandService.clockIn(
                    employeeId, clockInTime, ClockSource.WEB, null
            );

            // 本日18:00（JST）に退勤する（9時間勤務）
            Instant clockOutInstant = ZonedDateTime.now(ZONE_TOKYO)
                    .withHour(18).withMinute(0).withSecond(0).withNano(0)
                    .toInstant();
            ClockTime clockOutTime = new ClockTime(clockOutInstant);

            attendanceCommandService.clockOut(attendanceId, clockOutTime, ClockSource.WEB);

            // attendance_summariesから該当行を取得して検証する
            Map<String, Object> summary = jdbcTemplate.queryForMap(
                    "SELECT * FROM attendance_summaries WHERE attendance_id = ?",
                    attendanceId.value()
            );

            // ステータスがCLOCKED_OUTであることを検証する
            assertEquals("CLOCKED_OUT", summary.get("status"),
                    "退勤打刻後のステータスはCLOCKED_OUTであるべき");

            // 退勤時刻が記録されていることを検証する
            assertNotNull(summary.get("clock_out_time"),
                    "退勤時刻が記録されているべき");

            // 正味労働時間が0より大きいことを検証する
            int netWorkMinutes = (int) summary.get("net_work_minutes");
            assertTrue(netWorkMinutes > 0,
                    "退勤後の正味労働時間は0より大きいべき（実際: " + netWorkMinutes + "分）");
        }
    }

    // ========================================
    // シフトプロジェクターのテスト
    // ========================================

    @Nested
    @DisplayName("WeeklyScheduleSummaryProjector — 週次スケジュールサマリー更新")
    class WeeklyScheduleSummaryProjectorTest {

        @BeforeEach
        void setUp() {
            // Read Model・イベントストア・スケジュール・パターンをクリーンアップする
            // FK制約の順序に従い、子テーブルから先に削除する
            jdbcTemplate.execute("DELETE FROM weekly_schedule_summaries");
            jdbcTemplate.execute("DELETE FROM weekly_schedule_events");
            jdbcTemplate.execute("DELETE FROM weekly_schedules");
            jdbcTemplate.execute("DELETE FROM shift_patterns");
        }

        /**
         * 次の月曜日の日付を取得する（テスト用ヘルパー）
         *
         * <p>weekly_schedulesのCHECK制約（ISODOW=1）を満たすために、
         * 確実に月曜日の日付を返す。</p>
         */
        private LocalDate nextMonday() {
            LocalDate today = LocalDate.now();
            // 今日からの日数を計算して次の月曜日を取得する
            int daysUntilMonday = DayOfWeek.MONDAY.getValue() - today.getDayOfWeek().getValue();
            if (daysUntilMonday <= 0) {
                daysUntilMonday += 7; // 今日が月曜以降なら翌週の月曜にする
            }
            return today.plusDays(daysUntilMonday);
        }

        @Test
        @DisplayName("シフト割当 → weekly_schedule_summariesにDRAFTステータスとパターン名が作成される")
        void assignSchedule_createsWeeklyScheduleSummaryWithDraftStatus() {
            // テスト用のシフトパターンを2つ作成する（早番・遅番）
            ShiftPatternId earlyPatternId = shiftPatternCommandService.definePattern(
                    "早番_" + UUID.randomUUID().toString().substring(0, 4),
                    LocalTime.of(8, 0),   // 8:00開始
                    LocalTime.of(17, 0),  // 17:00終了
                    60,                    // 休憩60分
                    false                  // 夜勤なし
            );

            ShiftPatternId latePatternId = shiftPatternCommandService.definePattern(
                    "遅番_" + UUID.randomUUID().toString().substring(0, 4),
                    LocalTime.of(13, 0),  // 13:00開始
                    LocalTime.of(22, 0),  // 22:00終了
                    60,                    // 休憩60分
                    false                  // 夜勤なし
            );

            // テスト用のユニークな従業員IDを生成する
            EmployeeId employeeId = EmployeeId.of(UUID.randomUUID());

            // 次の月曜日を週開始日にする（CHECK制約: ISODOW=1）
            LocalDate weekStartDate = nextMonday();

            // 月曜〜金曜にシフトを割り当てる（月水金:早番、火木:遅番）
            Map<DayOfWeek, ShiftPatternId> assignments = Map.of(
                    DayOfWeek.MONDAY, earlyPatternId,
                    DayOfWeek.TUESDAY, latePatternId,
                    DayOfWeek.WEDNESDAY, earlyPatternId,
                    DayOfWeek.THURSDAY, latePatternId,
                    DayOfWeek.FRIDAY, earlyPatternId
            );

            // スケジュールを割り当てる（DRAFT状態で作成される）
            ScheduleId scheduleId = weeklyScheduleCommandService.assignSchedule(
                    employeeId, weekStartDate, assignments
            );

            // weekly_schedule_summariesから該当行を取得して検証する
            Map<String, Object> summary = jdbcTemplate.queryForMap(
                    "SELECT * FROM weekly_schedule_summaries WHERE weekly_schedule_id = ?",
                    scheduleId.value()
            );

            // ステータスがDRAFTであることを検証する
            assertEquals("DRAFT", summary.get("status"),
                    "初回割当後のステータスはDRAFTであるべき");

            // 従業員IDが正しく設定されていることを検証する
            assertEquals(employeeId.value(), summary.get("employee_id"),
                    "従業員IDが正しく設定されているべき");

            // 週開始日が正しく設定されていることを検証する
            assertEquals(weekStartDate, ((java.sql.Date) summary.get("week_start_date")).toLocalDate(),
                    "週開始日が正しく設定されているべき");

            // 割当日数が5であることを検証する（月〜金の5日間）
            // ※ JDBCはsmallintをIntegerとして返すため、intで比較する
            assertEquals(5, ((Number) summary.get("assigned_days")).intValue(),
                    "割当日数は5日であるべき");

            // 月曜日のパターンIDが設定されていることを検証する
            assertEquals(earlyPatternId.value(), summary.get("monday_pattern_id"),
                    "月曜日のパターンIDが早番であるべき");

            // 月曜日のパターン名が設定されていることを検証する
            assertNotNull(summary.get("monday_pattern_name"),
                    "月曜日のパターン名が設定されているべき");
            assertTrue(summary.get("monday_pattern_name").toString().startsWith("早番_"),
                    "月曜日のパターン名が早番であるべき");

            // 火曜日のパターンIDが遅番であることを検証する
            assertEquals(latePatternId.value(), summary.get("tuesday_pattern_id"),
                    "火曜日のパターンIDが遅番であるべき");

            // 土曜日は割当なし（null）であることを検証する
            assertNull(summary.get("saturday_pattern_id"),
                    "土曜日は割当なしなのでnullであるべき");

            // 日曜日は割当なし（null）であることを検証する
            assertNull(summary.get("sunday_pattern_id"),
                    "日曜日は割当なしなのでnullであるべき");

            // イベント数が1であることを検証する（ShiftAssignedEvent1件）
            assertEquals(1, summary.get("event_count"),
                    "割当後のイベント数は1であるべき");
        }

        @Test
        @DisplayName("シフト割当＋公開 → weekly_schedule_summariesのステータスがPUBLISHEDに更新される")
        void assignAndPublish_updatesStatusToPublished() {
            // テスト用のシフトパターンを1つ作成する
            ShiftPatternId patternId = shiftPatternCommandService.definePattern(
                    "日勤_" + UUID.randomUUID().toString().substring(0, 4),
                    LocalTime.of(9, 0),   // 9:00開始
                    LocalTime.of(18, 0),  // 18:00終了
                    60,                    // 休憩60分
                    false                  // 夜勤なし
            );

            // テスト用のユニークな従業員IDを生成する
            EmployeeId employeeId = EmployeeId.of(UUID.randomUUID());

            // 次の月曜日を週開始日にする（CHECK制約: ISODOW=1）
            LocalDate weekStartDate = nextMonday();

            // 月曜〜水曜にシフトを割り当てる
            Map<DayOfWeek, ShiftPatternId> assignments = Map.of(
                    DayOfWeek.MONDAY, patternId,
                    DayOfWeek.TUESDAY, patternId,
                    DayOfWeek.WEDNESDAY, patternId
            );

            // Step 1: スケジュールを割り当てる（DRAFT状態）
            ScheduleId scheduleId = weeklyScheduleCommandService.assignSchedule(
                    employeeId, weekStartDate, assignments
            );

            // DRAFT状態であることを確認する
            Map<String, Object> draftSummary = jdbcTemplate.queryForMap(
                    "SELECT status, event_count FROM weekly_schedule_summaries WHERE weekly_schedule_id = ?",
                    scheduleId.value()
            );
            assertEquals("DRAFT", draftSummary.get("status"),
                    "公開前のステータスはDRAFTであるべき");

            // Step 2: スケジュールを公開する（DRAFT→PUBLISHED）
            weeklyScheduleCommandService.publishSchedule(scheduleId);

            // weekly_schedule_summariesから該当行を取得して検証する
            Map<String, Object> publishedSummary = jdbcTemplate.queryForMap(
                    "SELECT * FROM weekly_schedule_summaries WHERE weekly_schedule_id = ?",
                    scheduleId.value()
            );

            // ステータスがPUBLISHEDに更新されていることを検証する
            assertEquals("PUBLISHED", publishedSummary.get("status"),
                    "公開後のステータスはPUBLISHEDであるべき");

            // イベント数が2であることを検証する（割当+公開の2イベント）
            assertEquals(2, publishedSummary.get("event_count"),
                    "割当＋公開後のイベント数は2であるべき");

            // パターンIDが保持されていることを検証する（公開でパターンは変わらない）
            assertEquals(patternId.value(), publishedSummary.get("monday_pattern_id"),
                    "公開後もパターンIDが保持されているべき");

            // パターン名が保持されていることを検証する
            assertNotNull(publishedSummary.get("monday_pattern_name"),
                    "公開後もパターン名が保持されているべき");
        }
    }
}
