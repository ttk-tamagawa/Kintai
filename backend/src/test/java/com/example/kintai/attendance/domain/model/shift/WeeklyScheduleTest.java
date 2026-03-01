package com.example.kintai.attendance.domain.model.shift;

import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WeeklySchedule 集約ルートの単体テスト
 *
 * <p>テスト対象:
 * <ul>
 *   <li>assign() — 新規スケジュール作成のファクトリメソッド</li>
 *   <li>changeAssignments() — 割当変更コマンド</li>
 *   <li>publish() — 公開コマンド</li>
 *   <li>unpublish() — 非公開コマンド</li>
 *   <li>getAssignments() — 不変コレクション返却の検証</li>
 *   <li>フルフロー — assign → changeAssignments → publish の一連フロー</li>
 * </ul>
 */
@DisplayName("WeeklySchedule 集約ルート")
class WeeklyScheduleTest {

    // ========================
    // テストデータ生成ヘルパー
    // ========================

    /** テスト用の従業員IDを生成する */
    private static EmployeeId testEmployeeId() {
        return EmployeeId.generate();
    }

    /** テスト用のシフトパターンIDを生成する */
    private static ShiftPatternId testShiftPatternId() {
        return ShiftPatternId.generate();
    }

    /**
     * テスト用の月曜日の日付を取得する
     * 2026-02-23 は月曜日
     */
    private static LocalDate mondayDate() {
        return LocalDate.of(2026, 2, 23);
    }

    /**
     * テスト用の火曜日の日付を取得する（異常系テスト用）
     * 2026-02-24 は火曜日
     */
    private static LocalDate tuesdayDate() {
        return LocalDate.of(2026, 2, 24);
    }

    /**
     * テスト用の割当マップを作成する（月曜〜金曜の5日分）
     */
    private static Map<DayOfWeek, ShiftPatternId> weekdayAssignments() {
        Map<DayOfWeek, ShiftPatternId> assignments = new EnumMap<>(DayOfWeek.class);
        assignments.put(DayOfWeek.MONDAY, testShiftPatternId());
        assignments.put(DayOfWeek.TUESDAY, testShiftPatternId());
        assignments.put(DayOfWeek.WEDNESDAY, testShiftPatternId());
        assignments.put(DayOfWeek.THURSDAY, testShiftPatternId());
        assignments.put(DayOfWeek.FRIDAY, testShiftPatternId());
        return assignments;
    }

    /**
     * テスト用の割当マップを作成する（月曜のみ1日分）
     */
    private static Map<DayOfWeek, ShiftPatternId> singleDayAssignment() {
        Map<DayOfWeek, ShiftPatternId> assignments = new EnumMap<>(DayOfWeek.class);
        assignments.put(DayOfWeek.MONDAY, testShiftPatternId());
        return assignments;
    }

    /**
     * テスト用のWeeklyScheduleをDRAFT状態で作成するヘルパー
     */
    private static WeeklySchedule createDraftSchedule() {
        return WeeklySchedule.assign(
                testEmployeeId(),
                mondayDate(),
                weekdayAssignments()
        );
    }

    /**
     * テスト用のWeeklyScheduleをPUBLISHED状態で作成するヘルパー
     */
    private static WeeklySchedule createPublishedSchedule() {
        WeeklySchedule schedule = createDraftSchedule();
        schedule.publish();
        return schedule;
    }

    // ========================
    // assign() テスト
    // ========================

    @Nested
    @DisplayName("assign() - 新規スケジュール作成")
    class AssignTest {

        @Test
        @DisplayName("正常系: DRAFTステータスでスケジュールが作成される")
        void assign_正常系_DRAFTステータスで作成される() {
            // テストデータ準備
            EmployeeId employeeId = testEmployeeId();
            LocalDate monday = mondayDate();
            Map<DayOfWeek, ShiftPatternId> assignments = weekdayAssignments();

            // 実行: 新規スケジュール作成
            WeeklySchedule schedule = WeeklySchedule.assign(employeeId, monday, assignments);

            // 検証: DRAFTステータスで作成されていること
            assertNotNull(schedule.getId(), "スケジュールIDが生成されていること");
            assertEquals(employeeId, schedule.getEmployeeId(), "従業員IDが設定されていること");
            assertEquals(monday, schedule.getWeekStartDate(), "週開始日が設定されていること");
            assertEquals(ScheduleStatus.DRAFT, schedule.getStatus(), "初期ステータスはDRAFTであること");
            assertEquals(0, schedule.getVersion(), "初期バージョンは0であること");
            assertNotNull(schedule.getCreatedAt(), "作成日時が設定されていること");
            assertNotNull(schedule.getUpdatedAt(), "更新日時が設定されていること");
        }

        @Test
        @DisplayName("正常系: 割当数が正しく設定される（5日分）")
        void assign_正常系_割当数が正しく設定される() {
            // テストデータ準備（月〜金の5日分）
            Map<DayOfWeek, ShiftPatternId> assignments = weekdayAssignments();

            // 実行
            WeeklySchedule schedule = WeeklySchedule.assign(
                    testEmployeeId(), mondayDate(), assignments
            );

            // 検証: 5日分の割当が登録されていること
            assertEquals(5, schedule.getAssignments().size(), "5日分の割当が設定されていること");
            assertTrue(schedule.getAssignments().containsKey(DayOfWeek.MONDAY), "月曜の割当があること");
            assertTrue(schedule.getAssignments().containsKey(DayOfWeek.FRIDAY), "金曜の割当があること");
            assertFalse(schedule.getAssignments().containsKey(DayOfWeek.SATURDAY), "土曜の割当がないこと");
            assertFalse(schedule.getAssignments().containsKey(DayOfWeek.SUNDAY), "日曜の割当がないこと");
        }

        @Test
        @DisplayName("正常系: 1日のみの割当でも作成できる")
        void assign_正常系_1日のみの割当で作成できる() {
            // テストデータ準備（月曜のみ1日分）
            Map<DayOfWeek, ShiftPatternId> assignments = singleDayAssignment();

            // 実行
            WeeklySchedule schedule = WeeklySchedule.assign(
                    testEmployeeId(), mondayDate(), assignments
            );

            // 検証: 1日分の割当が登録されていること
            assertEquals(1, schedule.getAssignments().size(), "1日分の割当が設定されていること");
        }

        @Test
        @DisplayName("異常系: 週開始日が火曜日の場合IllegalArgumentExceptionが発生する")
        void assign_異常系_火曜日指定でIllegalArgumentException() {
            // テストデータ準備（火曜日を指定）
            LocalDate tuesday = tuesdayDate();
            Map<DayOfWeek, ShiftPatternId> assignments = weekdayAssignments();

            // 実行・検証: 火曜日指定で例外が発生すること
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> WeeklySchedule.assign(testEmployeeId(), tuesday, assignments),
                    "火曜日指定でIllegalArgumentExceptionが発生すること"
            );

            // エラーメッセージに曜日情報が含まれていること
            assertTrue(
                    exception.getMessage().contains("月曜日"),
                    "エラーメッセージに「月曜日」が含まれること: " + exception.getMessage()
            );
        }

        @Test
        @DisplayName("異常系: 割当が空の場合IllegalArgumentExceptionが発生する")
        void assign_異常系_空の割当でIllegalArgumentException() {
            // テストデータ準備（空のマップ）
            Map<DayOfWeek, ShiftPatternId> emptyAssignments = new EnumMap<>(DayOfWeek.class);

            // 実行・検証: 空マップで例外が発生すること
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> WeeklySchedule.assign(testEmployeeId(), mondayDate(), emptyAssignments),
                    "空の割当でIllegalArgumentExceptionが発生すること"
            );

            // エラーメッセージに割当についてのメッセージが含まれていること
            assertTrue(
                    exception.getMessage().contains("割り当て"),
                    "エラーメッセージに割当の説明が含まれること: " + exception.getMessage()
            );
        }

        @Test
        @DisplayName("異常系: 割当がnullの場合IllegalArgumentExceptionが発生する")
        void assign_異常系_null割当でIllegalArgumentException() {
            // 実行・検証: null指定で例外が発生すること
            assertThrows(
                    IllegalArgumentException.class,
                    () -> WeeklySchedule.assign(testEmployeeId(), mondayDate(), null),
                    "null割当でIllegalArgumentExceptionが発生すること"
            );
        }
    }

    // ========================
    // changeAssignments() テスト
    // ========================

    @Nested
    @DisplayName("changeAssignments() - 割当変更")
    class ChangeAssignmentsTest {

        @Test
        @DisplayName("正常系: 割当が新しい内容に更新される")
        void changeAssignments_正常系_割当が更新される() {
            // テストデータ準備: DRAFT状態のスケジュールを作成（月〜金の5日分）
            WeeklySchedule schedule = createDraftSchedule();
            assertEquals(5, schedule.getAssignments().size(), "変更前は5日分の割当");

            // 新しい割当を作成（月〜水の3日分）
            Map<DayOfWeek, ShiftPatternId> newAssignments = new EnumMap<>(DayOfWeek.class);
            ShiftPatternId monPattern = testShiftPatternId();
            ShiftPatternId tuePattern = testShiftPatternId();
            ShiftPatternId wedPattern = testShiftPatternId();
            newAssignments.put(DayOfWeek.MONDAY, monPattern);
            newAssignments.put(DayOfWeek.TUESDAY, tuePattern);
            newAssignments.put(DayOfWeek.WEDNESDAY, wedPattern);

            // 実行: 割当を変更する
            schedule.changeAssignments(newAssignments);

            // 検証: 新しい割当に差し替わっていること
            assertEquals(3, schedule.getAssignments().size(), "変更後は3日分の割当");
            assertEquals(monPattern, schedule.getAssignments().get(DayOfWeek.MONDAY), "月曜の割当が新しいパターン");
            assertEquals(tuePattern, schedule.getAssignments().get(DayOfWeek.TUESDAY), "火曜の割当が新しいパターン");
            assertEquals(wedPattern, schedule.getAssignments().get(DayOfWeek.WEDNESDAY), "水曜の割当が新しいパターン");
            assertNull(schedule.getAssignments().get(DayOfWeek.THURSDAY), "木曜の割当がなくなっていること");
            assertNull(schedule.getAssignments().get(DayOfWeek.FRIDAY), "金曜の割当がなくなっていること");
        }

        @Test
        @DisplayName("正常系: PUBLISHED状態で変更するとDRAFTに戻る")
        void changeAssignments_正常系_PUBLISHEDからDRAFTに戻る() {
            // テストデータ準備: PUBLISHED状態のスケジュールを作成
            WeeklySchedule schedule = createPublishedSchedule();
            assertEquals(ScheduleStatus.PUBLISHED, schedule.getStatus(), "変更前はPUBLISHED");

            // 新しい割当を作成
            Map<DayOfWeek, ShiftPatternId> newAssignments = singleDayAssignment();

            // 実行: PUBLISHED状態で割当を変更する
            schedule.changeAssignments(newAssignments);

            // 検証: DRAFTに戻っていること（再公開が必要）
            assertEquals(
                    ScheduleStatus.DRAFT,
                    schedule.getStatus(),
                    "PUBLISHED状態で変更後はDRAFTに戻ること"
            );
        }

        @Test
        @DisplayName("正常系: DRAFT状態で変更してもDRAFTのまま")
        void changeAssignments_正常系_DRAFTのまま維持される() {
            // テストデータ準備: DRAFT状態のスケジュールを作成
            WeeklySchedule schedule = createDraftSchedule();
            assertEquals(ScheduleStatus.DRAFT, schedule.getStatus(), "変更前はDRAFT");

            // 新しい割当を作成
            Map<DayOfWeek, ShiftPatternId> newAssignments = singleDayAssignment();

            // 実行: DRAFT状態で割当を変更する
            schedule.changeAssignments(newAssignments);

            // 検証: DRAFTのままであること
            assertEquals(
                    ScheduleStatus.DRAFT,
                    schedule.getStatus(),
                    "DRAFT状態で変更後もDRAFTのままであること"
            );
        }

        @Test
        @DisplayName("異常系: 空の割当で変更するとIllegalArgumentExceptionが発生する")
        void changeAssignments_異常系_空の割当でIllegalArgumentException() {
            // テストデータ準備
            WeeklySchedule schedule = createDraftSchedule();
            Map<DayOfWeek, ShiftPatternId> emptyAssignments = new EnumMap<>(DayOfWeek.class);

            // 実行・検証: 空マップで例外が発生すること
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> schedule.changeAssignments(emptyAssignments),
                    "空の割当でIllegalArgumentExceptionが発生すること"
            );

            assertTrue(
                    exception.getMessage().contains("割り当て"),
                    "エラーメッセージに割当の説明が含まれること: " + exception.getMessage()
            );
        }

        @Test
        @DisplayName("異常系: nullの割当で変更するとIllegalArgumentExceptionが発生する")
        void changeAssignments_異常系_null割当でIllegalArgumentException() {
            // テストデータ準備
            WeeklySchedule schedule = createDraftSchedule();

            // 実行・検証: null指定で例外が発生すること
            assertThrows(
                    IllegalArgumentException.class,
                    () -> schedule.changeAssignments(null),
                    "null割当でIllegalArgumentExceptionが発生すること"
            );
        }
    }

    // ========================
    // publish() テスト
    // ========================

    @Nested
    @DisplayName("publish() - スケジュール公開")
    class PublishTest {

        @Test
        @DisplayName("正常系: DRAFT状態からPUBLISHEDに遷移する")
        void publish_正常系_DRAFTからPUBLISHEDに遷移() {
            // テストデータ準備: DRAFT状態のスケジュール
            WeeklySchedule schedule = createDraftSchedule();
            assertEquals(ScheduleStatus.DRAFT, schedule.getStatus(), "公開前はDRAFT");

            // 実行: スケジュールを公開する
            schedule.publish();

            // 検証: PUBLISHEDに遷移していること
            assertEquals(
                    ScheduleStatus.PUBLISHED,
                    schedule.getStatus(),
                    "公開後はPUBLISHEDに遷移していること"
            );
        }

        @Test
        @DisplayName("異常系: 既にPUBLISHED状態で公開するとIllegalStateExceptionが発生する")
        void publish_異常系_PUBLISHED状態で公開するとIllegalStateException() {
            // テストデータ準備: PUBLISHED状態のスケジュール
            WeeklySchedule schedule = createPublishedSchedule();
            assertEquals(ScheduleStatus.PUBLISHED, schedule.getStatus(), "既にPUBLISHED");

            // 実行・検証: PUBLISHED状態で公開すると例外が発生すること
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> schedule.publish(),
                    "PUBLISHED状態で公開するとIllegalStateExceptionが発生すること"
            );

            // エラーメッセージにステータス情報が含まれていること
            assertTrue(
                    exception.getMessage().contains("DRAFT"),
                    "エラーメッセージに「DRAFT」が含まれること: " + exception.getMessage()
            );
        }
    }

    // ========================
    // unpublish() テスト
    // ========================

    @Nested
    @DisplayName("unpublish() - スケジュール非公開")
    class UnpublishTest {

        @Test
        @DisplayName("正常系: PUBLISHED状態からDRAFTに遷移する")
        void unpublish_正常系_PUBLISHEDからDRAFTに遷移() {
            // テストデータ準備: PUBLISHED状態のスケジュール
            WeeklySchedule schedule = createPublishedSchedule();
            assertEquals(ScheduleStatus.PUBLISHED, schedule.getStatus(), "非公開前はPUBLISHED");

            // 実行: スケジュールを非公開にする
            schedule.unpublish();

            // 検証: DRAFTに遷移していること
            assertEquals(
                    ScheduleStatus.DRAFT,
                    schedule.getStatus(),
                    "非公開後はDRAFTに遷移していること"
            );
        }

        @Test
        @DisplayName("異常系: DRAFT状態で非公開にするとIllegalStateExceptionが発生する")
        void unpublish_異常系_DRAFT状態で非公開にするとIllegalStateException() {
            // テストデータ準備: DRAFT状態のスケジュール
            WeeklySchedule schedule = createDraftSchedule();
            assertEquals(ScheduleStatus.DRAFT, schedule.getStatus(), "既にDRAFT");

            // 実行・検証: DRAFT状態で非公開にすると例外が発生すること
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> schedule.unpublish(),
                    "DRAFT状態で非公開にするとIllegalStateExceptionが発生すること"
            );

            // エラーメッセージにステータス情報が含まれていること
            assertTrue(
                    exception.getMessage().contains("PUBLISHED"),
                    "エラーメッセージに「PUBLISHED」が含まれること: " + exception.getMessage()
            );
        }
    }

    // ========================
    // getAssignments() 不変性テスト
    // ========================

    @Nested
    @DisplayName("getAssignments() - 不変コレクション")
    class GetAssignmentsTest {

        @Test
        @DisplayName("getAssignments()が返すMapは変更不可である")
        void getAssignments_変更不可のMapが返される() {
            // テストデータ準備
            WeeklySchedule schedule = createDraftSchedule();
            Map<DayOfWeek, ShiftPatternId> assignments = schedule.getAssignments();

            // 実行・検証: 外部からの変更操作でUnsupportedOperationExceptionが発生すること
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> assignments.put(DayOfWeek.SATURDAY, testShiftPatternId()),
                    "put操作でUnsupportedOperationExceptionが発生すること"
            );

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> assignments.remove(DayOfWeek.MONDAY),
                    "remove操作でUnsupportedOperationExceptionが発生すること"
            );

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> assignments.clear(),
                    "clear操作でUnsupportedOperationExceptionが発生すること"
            );
        }
    }

    // ========================
    // フルフロー テスト
    // ========================

    @Nested
    @DisplayName("フルフロー - assign → changeAssignments → publish")
    class FullFlowTest {

        @Test
        @DisplayName("assign → changeAssignments → publish の一連フローが成功する")
        void fullFlow_assign_change_publish_成功() {
            // Step 1: 新規スケジュール作成（DRAFT状態）
            EmployeeId employeeId = testEmployeeId();
            Map<DayOfWeek, ShiftPatternId> initialAssignments = weekdayAssignments();

            WeeklySchedule schedule = WeeklySchedule.assign(
                    employeeId, mondayDate(), initialAssignments
            );

            // 検証: DRAFT状態で5日分の割当
            assertEquals(ScheduleStatus.DRAFT, schedule.getStatus(), "Step1: DRAFT状態");
            assertEquals(5, schedule.getAssignments().size(), "Step1: 5日分の割当");
            assertEquals(employeeId, schedule.getEmployeeId(), "Step1: 従業員IDが一致");

            // Step 2: 割当を変更（月・水・金の3日分に変更）
            Map<DayOfWeek, ShiftPatternId> updatedAssignments = new EnumMap<>(DayOfWeek.class);
            ShiftPatternId newPatternId = testShiftPatternId();
            updatedAssignments.put(DayOfWeek.MONDAY, newPatternId);
            updatedAssignments.put(DayOfWeek.WEDNESDAY, testShiftPatternId());
            updatedAssignments.put(DayOfWeek.FRIDAY, testShiftPatternId());

            schedule.changeAssignments(updatedAssignments);

            // 検証: DRAFT状態のままで3日分の割当に変更
            assertEquals(ScheduleStatus.DRAFT, schedule.getStatus(), "Step2: DRAFTのまま");
            assertEquals(3, schedule.getAssignments().size(), "Step2: 3日分の割当に変更");
            assertEquals(newPatternId, schedule.getAssignments().get(DayOfWeek.MONDAY),
                    "Step2: 月曜の割当が新しいパターン");

            // Step 3: 公開
            schedule.publish();

            // 検証: PUBLISHED状態
            assertEquals(ScheduleStatus.PUBLISHED, schedule.getStatus(), "Step3: PUBLISHED状態");
            assertEquals(3, schedule.getAssignments().size(), "Step3: 割当数は変わらず3日分");
        }

        @Test
        @DisplayName("assign → publish → changeAssignments → publish の再公開フローが成功する")
        void fullFlow_assign_publish_change_republish_成功() {
            // Step 1: 新規スケジュール作成して即公開
            WeeklySchedule schedule = WeeklySchedule.assign(
                    testEmployeeId(), mondayDate(), weekdayAssignments()
            );
            schedule.publish();
            assertEquals(ScheduleStatus.PUBLISHED, schedule.getStatus(), "Step1: PUBLISHED状態");

            // Step 2: 公開済みスケジュールの割当を変更（DRAFTに戻る）
            Map<DayOfWeek, ShiftPatternId> newAssignments = singleDayAssignment();
            schedule.changeAssignments(newAssignments);
            assertEquals(ScheduleStatus.DRAFT, schedule.getStatus(), "Step2: DRAFTに戻った");
            assertEquals(1, schedule.getAssignments().size(), "Step2: 1日分の割当に変更");

            // Step 3: 再公開
            schedule.publish();
            assertEquals(ScheduleStatus.PUBLISHED, schedule.getStatus(), "Step3: 再びPUBLISHED状態");
        }

        @Test
        @DisplayName("assign → publish → unpublish → publish の公開・非公開サイクルが成功する")
        void fullFlow_assign_publish_unpublish_republish_成功() {
            // Step 1: 新規スケジュール作成して公開
            WeeklySchedule schedule = WeeklySchedule.assign(
                    testEmployeeId(), mondayDate(), weekdayAssignments()
            );
            schedule.publish();
            assertEquals(ScheduleStatus.PUBLISHED, schedule.getStatus(), "Step1: PUBLISHED状態");

            // Step 2: 非公開にする（DRAFTに戻る）
            schedule.unpublish();
            assertEquals(ScheduleStatus.DRAFT, schedule.getStatus(), "Step2: DRAFTに戻った");
            assertEquals(5, schedule.getAssignments().size(), "Step2: 割当数は変わらず5日分");

            // Step 3: 再公開
            schedule.publish();
            assertEquals(ScheduleStatus.PUBLISHED, schedule.getStatus(), "Step3: 再びPUBLISHED状態");
        }
    }
}
