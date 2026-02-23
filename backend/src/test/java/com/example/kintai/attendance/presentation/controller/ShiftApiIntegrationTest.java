package com.example.kintai.attendance.presentation.controller;

import com.example.kintai.attendance.presentation.dto.AssignScheduleRequest;
import com.example.kintai.attendance.presentation.dto.ChangeScheduleRequest;
import com.example.kintai.attendance.presentation.dto.DefinePatternRequest;
import com.example.kintai.attendance.presentation.dto.PatternResponse;
import com.example.kintai.attendance.presentation.dto.ScheduleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * シフトAPI統合テスト（タスク 9-2-3）
 *
 * <p>シフトパターン管理API（5-6）と週次スケジュール管理API（5-7）の一連フローを
 * 実際のHTTPリクエストで検証する。DBはテスト用PostgreSQL（kintai_test）を使用する。</p>
 *
 * <p>テスト対象:
 * <ul>
 *   <li>パターン作成（POST /api/v1/shifts/patterns）</li>
 *   <li>パターン一覧（GET /api/v1/shifts/patterns）</li>
 *   <li>パターン詳細（GET /api/v1/shifts/patterns/{id}）</li>
 *   <li>パターン無効化（POST /api/v1/shifts/patterns/{id}/actions/deactivate）</li>
 *   <li>パターン再有効化（POST /api/v1/shifts/patterns/{id}/actions/reactivate）</li>
 *   <li>スケジュール割当（POST /api/v1/shifts/schedules）</li>
 *   <li>スケジュール変更（PUT /api/v1/shifts/schedules/{id}）</li>
 *   <li>スケジュール公開（POST /api/v1/shifts/schedules/{id}/actions/publish）</li>
 * </ul>
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShiftApiIntegrationTest {

    /** HTTPリクエストを送信するテスト用クライアント */
    @Autowired
    private TestRestTemplate restTemplate;

    /** テストデータのクリーンアップに使用するJDBCテンプレート */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // パターンAPIのベースURL
    private static final String PATTERNS_URL = "/api/v1/shifts/patterns";

    // スケジュールAPIのベースURL
    private static final String SCHEDULES_URL = "/api/v1/shifts/schedules";

    // テスト用の従業員ID（weekly_schedulesにはemployeesテーブルへのFK制約がないためランダムUUIDを使用）
    private static final UUID TEST_EMPLOYEE_ID = UUID.randomUUID();

    // テスト用の週開始日（月曜日であること）
    private static final LocalDate WEEK_START_DATE = LocalDate.of(2026, 3, 2);

    /**
     * 各テスト実行前にテストデータをクリーンアップする
     *
     * <p>FK制約を考慮して子テーブルから順に削除する:
     * weekly_schedule_summaries → weekly_schedule_events → weekly_schedules → shift_patterns</p>
     */
    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM weekly_schedule_summaries");
        jdbcTemplate.execute("DELETE FROM weekly_schedule_events");
        jdbcTemplate.execute("DELETE FROM weekly_schedules");
        jdbcTemplate.execute("DELETE FROM shift_patterns");
    }

    // ========================================
    // ヘルパーメソッド
    // ========================================

    /**
     * シフトパターンを作成するヘルパー — POST /api/v1/shifts/patterns を呼び出す
     *
     * @param name         パターン名
     * @param startTime    勤務開始時刻
     * @param endTime      勤務終了時刻
     * @param breakMinutes 休憩時間（分）
     * @param isOvernight  夜勤フラグ
     * @return APIレスポンス
     */
    private ResponseEntity<PatternResponse> createPattern(
            String name, LocalTime startTime, LocalTime endTime,
            int breakMinutes, boolean isOvernight) {

        DefinePatternRequest request = new DefinePatternRequest(
                name, startTime, endTime, breakMinutes, isOvernight);

        return restTemplate.postForEntity(PATTERNS_URL, request, PatternResponse.class);
    }

    /**
     * 標準日勤パターン（早番 9:00-18:00）を作成するヘルパー
     */
    private PatternResponse createEarlyPattern() {
        ResponseEntity<PatternResponse> response = createPattern(
                "早番", LocalTime.of(9, 0), LocalTime.of(18, 0), 60, false);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    /**
     * 遅番パターン（14:00-23:00）を作成するヘルパー
     */
    private PatternResponse createLatePattern() {
        ResponseEntity<PatternResponse> response = createPattern(
                "遅番", LocalTime.of(14, 0), LocalTime.of(23, 0), 60, false);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    // ========================================
    // テストケース: シフトパターンAPI
    // ========================================

    @Nested
    @DisplayName("シフトパターンAPI")
    class ShiftPatternApiTest {

        @Test
        @DisplayName("パターン作成 — 201で正しいデータが返却される")
        void createPattern_returns201WithCorrectData() {
            // 早番パターンの作成リクエストを送信する
            ResponseEntity<PatternResponse> response = createPattern(
                    "早番", LocalTime.of(9, 0), LocalTime.of(18, 0), 60, false);

            // 201 Created が返却されること
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // レスポンスボディの内容を検証する
            PatternResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.patternId()).isNotNull();
            assertThat(body.name()).isEqualTo("早番");
            assertThat(body.startTime()).isEqualTo("09:00");
            assertThat(body.endTime()).isEqualTo("18:00");
            assertThat(body.breakMinutes()).isEqualTo(60);
            assertThat(body.isOvernight()).isFalse();
            assertThat(body.isActive()).isTrue();
        }

        @Test
        @DisplayName("パターン一覧取得 — 作成したパターンが一覧に含まれる")
        void getPatterns_returnsCreatedPatterns() {
            // 2つのパターンを作成する
            createEarlyPattern();
            createLatePattern();

            // パターン一覧を取得する
            ResponseEntity<List<PatternResponse>> response = restTemplate.exchange(
                    PATTERNS_URL,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<PatternResponse>>() {}
            );

            // 200 OK で2件のパターンが返却されること
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<PatternResponse> patterns = response.getBody();
            assertThat(patterns).hasSize(2);

            // パターン名でソートされていることを検証する（名前昇順）
            List<String> names = patterns.stream().map(PatternResponse::name).toList();
            assertThat(names).containsExactly("早番", "遅番");
        }

        @Test
        @DisplayName("パターン詳細取得 — IDで正しいパターンが返却される")
        void getPatternById_returnsCorrectDetail() {
            // パターンを作成する
            PatternResponse created = createEarlyPattern();

            // IDでパターン詳細を取得する
            ResponseEntity<PatternResponse> response = restTemplate.getForEntity(
                    PATTERNS_URL + "/" + created.patternId(), PatternResponse.class);

            // 200 OK で正しいパターンが返却されること
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PatternResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.patternId()).isEqualTo(created.patternId());
            assertThat(body.name()).isEqualTo("早番");
            assertThat(body.startTime()).isEqualTo("09:00");
            assertThat(body.endTime()).isEqualTo("18:00");
            assertThat(body.breakMinutes()).isEqualTo(60);
            assertThat(body.isOvernight()).isFalse();
            assertThat(body.isActive()).isTrue();
        }

        @Test
        @DisplayName("パターン無効化 — isActiveがfalseになる")
        void deactivatePattern_setsIsActiveToFalse() {
            // パターンを作成する（初期状態: isActive=true）
            PatternResponse created = createEarlyPattern();

            // パターンを無効化する
            ResponseEntity<PatternResponse> response = restTemplate.postForEntity(
                    PATTERNS_URL + "/" + created.patternId() + "/actions/deactivate",
                    null, PatternResponse.class);

            // 200 OK で isActive=false が返却されること
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PatternResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.isActive()).isFalse();
            assertThat(body.patternId()).isEqualTo(created.patternId());
            assertThat(body.name()).isEqualTo("早番");
        }

        @Test
        @DisplayName("パターン再有効化 — isActiveがtrueに戻る")
        void reactivatePattern_setsIsActiveToTrue() {
            // パターンを作成して無効化する
            PatternResponse created = createEarlyPattern();
            restTemplate.postForEntity(
                    PATTERNS_URL + "/" + created.patternId() + "/actions/deactivate",
                    null, PatternResponse.class);

            // パターンを再有効化する
            ResponseEntity<PatternResponse> response = restTemplate.postForEntity(
                    PATTERNS_URL + "/" + created.patternId() + "/actions/reactivate",
                    null, PatternResponse.class);

            // 200 OK で isActive=true が返却されること
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PatternResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.isActive()).isTrue();
            assertThat(body.patternId()).isEqualTo(created.patternId());
        }

        @Test
        @DisplayName("バリデーション — パターン名が空の場合は400が返却される")
        void createPattern_withMissingName_returns400() {
            // パターン名を空文字で送信する
            DefinePatternRequest request = new DefinePatternRequest(
                    "", LocalTime.of(9, 0), LocalTime.of(18, 0), 60, false);

            ResponseEntity<ProblemDetail> response = restTemplate.postForEntity(
                    PATTERNS_URL, request, ProblemDetail.class);

            // 400 Bad Request が返却されること
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ========================================
    // テストケース: 週次スケジュールAPI（フルフロー）
    // ========================================

    @Nested
    @DisplayName("週次スケジュールAPI")
    class WeeklyScheduleApiTest {

        @Test
        @DisplayName("フルフロー — パターン作成 → スケジュール割当 → スケジュール変更 → スケジュール公開")
        void fullScheduleFlow_createPatternsAssignChangePublish() {
            // --- Step 1: 2つのシフトパターンを作成する ---
            PatternResponse earlyPattern = createEarlyPattern();
            PatternResponse latePattern = createLatePattern();

            // --- Step 2: 月〜金に早番パターンを割り当ててスケジュールを作成する ---
            Map<String, UUID> assignments = new HashMap<>();
            assignments.put("MONDAY", earlyPattern.patternId());
            assignments.put("TUESDAY", earlyPattern.patternId());
            assignments.put("WEDNESDAY", earlyPattern.patternId());
            assignments.put("THURSDAY", earlyPattern.patternId());
            assignments.put("FRIDAY", earlyPattern.patternId());

            AssignScheduleRequest assignRequest = new AssignScheduleRequest(
                    TEST_EMPLOYEE_ID, WEEK_START_DATE, assignments);

            ResponseEntity<ScheduleResponse> assignResponse = restTemplate.postForEntity(
                    SCHEDULES_URL, assignRequest, ScheduleResponse.class);

            // 201 Created で DRAFT ステータスが返却されること
            assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            ScheduleResponse assignedSchedule = assignResponse.getBody();
            assertThat(assignedSchedule).isNotNull();
            assertThat(assignedSchedule.scheduleId()).isNotNull();
            assertThat(assignedSchedule.employeeId()).isEqualTo(TEST_EMPLOYEE_ID);
            assertThat(assignedSchedule.weekStartDate()).isEqualTo(WEEK_START_DATE);
            assertThat(assignedSchedule.status()).isEqualTo("DRAFT");
            assertThat(assignedSchedule.assignedDays()).isEqualTo(5);
            // 月曜の割当を検証する
            assertThat(assignedSchedule.assignments()).containsKey("MONDAY");
            assertThat(assignedSchedule.assignments().get("MONDAY").patternId())
                    .isEqualTo(earlyPattern.patternId());
            assertThat(assignedSchedule.assignments().get("MONDAY").patternName())
                    .isEqualTo("早番");

            UUID scheduleId = assignedSchedule.scheduleId();

            // --- Step 3: 月〜水を遅番、木金を早番に変更する ---
            Map<String, UUID> newAssignments = new HashMap<>();
            newAssignments.put("MONDAY", latePattern.patternId());
            newAssignments.put("TUESDAY", latePattern.patternId());
            newAssignments.put("WEDNESDAY", latePattern.patternId());
            newAssignments.put("THURSDAY", earlyPattern.patternId());
            newAssignments.put("FRIDAY", earlyPattern.patternId());

            ChangeScheduleRequest changeRequest = new ChangeScheduleRequest(newAssignments);

            ResponseEntity<ScheduleResponse> changeResponse = restTemplate.exchange(
                    SCHEDULES_URL + "/" + scheduleId,
                    HttpMethod.PUT,
                    new HttpEntity<>(changeRequest),
                    ScheduleResponse.class);

            // 200 OK で変更後のスケジュールが返却されること
            assertThat(changeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            ScheduleResponse changedSchedule = changeResponse.getBody();
            assertThat(changedSchedule).isNotNull();
            assertThat(changedSchedule.status()).isEqualTo("DRAFT");
            // 月曜の割当が遅番に変更されていること
            assertThat(changedSchedule.assignments().get("MONDAY").patternId())
                    .isEqualTo(latePattern.patternId());
            assertThat(changedSchedule.assignments().get("MONDAY").patternName())
                    .isEqualTo("遅番");
            // 木曜の割当が早番のままであること
            assertThat(changedSchedule.assignments().get("THURSDAY").patternId())
                    .isEqualTo(earlyPattern.patternId());

            // --- Step 4: スケジュールを公開する ---
            ResponseEntity<ScheduleResponse> publishResponse = restTemplate.postForEntity(
                    SCHEDULES_URL + "/" + scheduleId + "/actions/publish",
                    null, ScheduleResponse.class);

            // 200 OK で PUBLISHED ステータスが返却されること
            assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            ScheduleResponse publishedSchedule = publishResponse.getBody();
            assertThat(publishedSchedule).isNotNull();
            assertThat(publishedSchedule.status()).isEqualTo("PUBLISHED");
            assertThat(publishedSchedule.assignedDays()).isEqualTo(5);
        }

        @Test
        @DisplayName("無効パターンでの割当 — INACTIVEパターンを指定するとエラーが返却される")
        void assignSchedule_withInactivePattern_returnsError() {
            // パターンを作成して無効化する
            PatternResponse pattern = createEarlyPattern();
            restTemplate.postForEntity(
                    PATTERNS_URL + "/" + pattern.patternId() + "/actions/deactivate",
                    null, PatternResponse.class);

            // 無効化されたパターンでスケジュールを割り当てる
            Map<String, UUID> assignments = new HashMap<>();
            assignments.put("MONDAY", pattern.patternId());

            AssignScheduleRequest request = new AssignScheduleRequest(
                    TEST_EMPLOYEE_ID, WEEK_START_DATE, assignments);

            ResponseEntity<ProblemDetail> response = restTemplate.postForEntity(
                    SCHEDULES_URL, request, ProblemDetail.class);

            // INACTIVEパターンの使用は IllegalArgumentException → 404 Not Found が返却されること
            // （GlobalExceptionHandler が IllegalArgumentException を 404 にマッピングする）
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("公開済みスケジュールの再公開 — 409 Conflictが返却される")
        void publishAlreadyPublishedSchedule_returns409() {
            // パターンを作成してスケジュールを割り当てる
            PatternResponse pattern = createEarlyPattern();

            Map<String, UUID> assignments = new HashMap<>();
            assignments.put("MONDAY", pattern.patternId());
            assignments.put("TUESDAY", pattern.patternId());

            AssignScheduleRequest assignRequest = new AssignScheduleRequest(
                    TEST_EMPLOYEE_ID, WEEK_START_DATE, assignments);

            ResponseEntity<ScheduleResponse> assignResponse = restTemplate.postForEntity(
                    SCHEDULES_URL, assignRequest, ScheduleResponse.class);

            UUID scheduleId = assignResponse.getBody().scheduleId();

            // 1回目の公開（成功するはず）
            ResponseEntity<ScheduleResponse> firstPublish = restTemplate.postForEntity(
                    SCHEDULES_URL + "/" + scheduleId + "/actions/publish",
                    null, ScheduleResponse.class);
            assertThat(firstPublish.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 2回目の公開（既にPUBLISHED → IllegalStateException → 409 Conflict）
            ResponseEntity<ProblemDetail> secondPublish = restTemplate.postForEntity(
                    SCHEDULES_URL + "/" + scheduleId + "/actions/publish",
                    null, ProblemDetail.class);

            assertThat(secondPublish.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }
}
