package com.example.kintai.attendance.presentation.controller;

import com.example.kintai.attendance.presentation.dto.BreakEndResponse;
import com.example.kintai.attendance.presentation.dto.BreakStartResponse;
import com.example.kintai.attendance.presentation.dto.ClockInResponse;
import com.example.kintai.attendance.presentation.dto.ClockOutResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 勤怠打刻APIの統合テスト — コントローラからDB永続化まで全レイヤーを通した結合テスト
 *
 * <p>TestRestTemplateを使用して実際のHTTPリクエストを送信し、
 * Spring Boot組み込みサーバーを起動した状態でエンドポイントの動作を検証する。</p>
 *
 * <p>テスト対象エンドポイント:
 * <ul>
 *   <li>POST /api/v1/attendances/clock-in — 出勤打刻</li>
 *   <li>POST /api/v1/attendances/clock-out — 退勤打刻</li>
 *   <li>POST /api/v1/attendances/break-start — 休憩開始</li>
 *   <li>POST /api/v1/attendances/break-end — 休憩終了</li>
 * </ul>
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("勤怠打刻API 統合テスト")
class AttendanceCommandControllerIntegrationTest {

    /** HTTPリクエスト送信用のテストクライアント */
    @Autowired
    private TestRestTemplate restTemplate;

    /** テストデータのクリーンアップ用JDBCテンプレート */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** APIのベースパス */
    private static final String BASE_PATH = "/api/v1/attendances";

    // ========================================
    // 各テストで使用する固定の従業員UUID（テスト間の衝突を避けるためテストごとに異なるUUIDを使用）
    // ========================================

    /** テスト1用の従業員UUID: 出勤打刻の正常系テスト */
    private static final UUID EMPLOYEE_CLOCK_IN = UUID.fromString("10000000-0000-0000-0000-000000000001");

    /** テスト2用の従業員UUID: フルフロー（出勤→休憩開始→休憩終了→退勤）テスト */
    private static final UUID EMPLOYEE_FULL_FLOW = UUID.fromString("10000000-0000-0000-0000-000000000002");

    /** テスト3用の従業員UUID: 出勤なしで退勤打刻のエラーテスト */
    private static final UUID EMPLOYEE_NO_CLOCK_IN = UUID.fromString("10000000-0000-0000-0000-000000000003");

    /** テスト5用の従業員UUID: 二重出勤のエラーテスト */
    private static final UUID EMPLOYEE_DOUBLE_CLOCK_IN = UUID.fromString("10000000-0000-0000-0000-000000000005");

    /**
     * 各テスト実行前にテストデータをクリーンアップする
     *
     * <p>FK制約があるため、clock_entries → attendance_events → attendances の順に削除する。
     * 読み取りモデル（attendance_summaries）も合わせて削除する。</p>
     */
    @BeforeEach
    void cleanUp() {
        // テスト用従業員のデータをFK制約の子テーブルから順番に削除する
        UUID[] testEmployees = {
                EMPLOYEE_CLOCK_IN,
                EMPLOYEE_FULL_FLOW,
                EMPLOYEE_NO_CLOCK_IN,
                EMPLOYEE_DOUBLE_CLOCK_IN
        };

        for (UUID employeeId : testEmployees) {
            // attendance_summariesを削除（読み取りモデル）
            jdbcTemplate.update(
                    "DELETE FROM attendance_summaries WHERE employee_id = ?",
                    employeeId
            );
            // clock_entriesを削除（打刻ログ。attendances.idを参照するFK）
            jdbcTemplate.update(
                    "DELETE FROM clock_entries WHERE attendance_id IN " +
                            "(SELECT id FROM attendances WHERE employee_id = ?)",
                    employeeId
            );
            // attendance_eventsを削除（イベントログ。attendances.idを参照するFK）
            jdbcTemplate.update(
                    "DELETE FROM attendance_events WHERE attendance_id IN " +
                            "(SELECT id FROM attendances WHERE employee_id = ?)",
                    employeeId
            );
            // attendancesを削除（集約ルート）
            jdbcTemplate.update(
                    "DELETE FROM attendances WHERE employee_id = ?",
                    employeeId
            );
        }
    }

    // ========================================
    // テスト1: 出勤打刻の正常系
    // ========================================

    @Test
    @Order(1)
    @DisplayName("出勤打刻が成功し、CLOCKED_INステータスで200が返る")
    void clockIn_shouldReturn200WithClockedInStatus() {
        // リクエストボディを組み立てる（出勤打刻）
        Map<String, Object> request = Map.of(
                "employeeId", EMPLOYEE_CLOCK_IN.toString(),
                "clockTime", "2026-02-23T09:00:00+09:00",
                "source", "WEB"
        );

        // POST /api/v1/attendances/clock-in を実行する
        ResponseEntity<ClockInResponse> response = restTemplate.postForEntity(
                BASE_PATH + "/clock-in",
                request,
                ClockInResponse.class
        );

        // HTTPステータス200（OK）が返ることを検証する
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "出勤打刻が成功して200が返ること");

        // レスポンスボディの検証
        ClockInResponse body = response.getBody();
        assertNotNull(body, "レスポンスボディがnullでないこと");
        assertNotNull(body.attendanceId(), "勤怠記録IDが返ること");
        assertEquals(EMPLOYEE_CLOCK_IN, body.employeeId(), "従業員IDが一致すること");
        assertEquals("CLOCKED_IN", body.status(), "ステータスがCLOCKED_INであること");
        assertNotNull(body.clockIn(), "出勤時刻が返ること");
        assertEquals("WEB", body.source(), "打刻元がWEBであること");
        assertNotNull(body.updatedAt(), "更新日時が返ること");
    }

    // ========================================
    // テスト2: フルフロー（出勤→休憩開始→休憩終了→退勤）
    // ========================================

    @Test
    @Order(2)
    @DisplayName("出勤→休憩開始→休憩終了→退勤のフルフローが成功し、DBに正しく保存される")
    void fullFlow_clockInBreakClockOut_shouldSucceedAndPersistToDb() {
        // === Step 1: 出勤打刻（09:00） ===
        Map<String, Object> clockInRequest = Map.of(
                "employeeId", EMPLOYEE_FULL_FLOW.toString(),
                "clockTime", "2026-02-23T09:00:00+09:00",
                "source", "WEB"
        );

        ResponseEntity<ClockInResponse> clockInResponse = restTemplate.postForEntity(
                BASE_PATH + "/clock-in",
                clockInRequest,
                ClockInResponse.class
        );

        // 出勤打刻が成功したことを検証する
        assertEquals(HttpStatus.OK, clockInResponse.getStatusCode(),
                "Step1: 出勤打刻が成功すること");
        ClockInResponse clockInBody = clockInResponse.getBody();
        assertNotNull(clockInBody, "Step1: レスポンスボディがnullでないこと");
        assertEquals("CLOCKED_IN", clockInBody.status(),
                "Step1: ステータスがCLOCKED_INであること");

        // === Step 2: 休憩開始（12:00） ===
        Map<String, Object> breakStartRequest = Map.of(
                "employeeId", EMPLOYEE_FULL_FLOW.toString(),
                "clockTime", "2026-02-23T12:00:00+09:00",
                "source", "WEB"
        );

        ResponseEntity<BreakStartResponse> breakStartResponse = restTemplate.postForEntity(
                BASE_PATH + "/break-start",
                breakStartRequest,
                BreakStartResponse.class
        );

        // 休憩開始が成功したことを検証する
        assertEquals(HttpStatus.OK, breakStartResponse.getStatusCode(),
                "Step2: 休憩開始が成功すること");
        BreakStartResponse breakStartBody = breakStartResponse.getBody();
        assertNotNull(breakStartBody, "Step2: レスポンスボディがnullでないこと");
        assertEquals("CLOCKED_IN", breakStartBody.status(),
                "Step2: ステータスがCLOCKED_INのままであること");
        assertTrue(breakStartBody.onBreak(),
                "Step2: 休憩中フラグがtrueであること");

        // === Step 3: 休憩終了（13:00） ===
        Map<String, Object> breakEndRequest = Map.of(
                "employeeId", EMPLOYEE_FULL_FLOW.toString(),
                "clockTime", "2026-02-23T13:00:00+09:00",
                "source", "WEB"
        );

        ResponseEntity<BreakEndResponse> breakEndResponse = restTemplate.postForEntity(
                BASE_PATH + "/break-end",
                breakEndRequest,
                BreakEndResponse.class
        );

        // 休憩終了が成功したことを検証する
        assertEquals(HttpStatus.OK, breakEndResponse.getStatusCode(),
                "Step3: 休憩終了が成功すること");
        BreakEndResponse breakEndBody = breakEndResponse.getBody();
        assertNotNull(breakEndBody, "Step3: レスポンスボディがnullでないこと");
        assertEquals("CLOCKED_IN", breakEndBody.status(),
                "Step3: ステータスがCLOCKED_INのままであること");
        assertFalse(breakEndBody.onBreak(),
                "Step3: 休憩中フラグがfalseであること");
        assertEquals(60, breakEndBody.breakMinutes(),
                "Step3: 休憩時間が60分であること（12:00〜13:00）");

        // === Step 4: 退勤打刻（18:00） ===
        Map<String, Object> clockOutRequest = Map.of(
                "employeeId", EMPLOYEE_FULL_FLOW.toString(),
                "clockTime", "2026-02-23T18:00:00+09:00",
                "source", "WEB"
        );

        ResponseEntity<ClockOutResponse> clockOutResponse = restTemplate.postForEntity(
                BASE_PATH + "/clock-out",
                clockOutRequest,
                ClockOutResponse.class
        );

        // 退勤打刻が成功したことを検証する
        assertEquals(HttpStatus.OK, clockOutResponse.getStatusCode(),
                "Step4: 退勤打刻が成功すること");
        ClockOutResponse clockOutBody = clockOutResponse.getBody();
        assertNotNull(clockOutBody, "Step4: レスポンスボディがnullでないこと");
        assertEquals("CLOCKED_OUT", clockOutBody.status(),
                "Step4: ステータスがCLOCKED_OUTであること");
        assertNotNull(clockOutBody.clockIn(),
                "Step4: 出勤時刻がレスポンスに含まれること");
        assertNotNull(clockOutBody.clockOut(),
                "Step4: 退勤時刻がレスポンスに含まれること");
        assertEquals(60, clockOutBody.breakMinutes(),
                "Step4: 休憩時間が60分であること");

        // === DBの検証: attendancesテーブルにレコードが存在すること ===
        Integer attendanceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendances WHERE employee_id = ?",
                Integer.class,
                EMPLOYEE_FULL_FLOW
        );
        assertEquals(1, attendanceCount,
                "DB検証: attendancesテーブルにレコードが1件存在すること");

        // === DBの検証: ステータスがCLOCKED_OUTであること ===
        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM attendances WHERE employee_id = ?",
                String.class,
                EMPLOYEE_FULL_FLOW
        );
        assertEquals("CLOCKED_OUT", dbStatus,
                "DB検証: ステータスがCLOCKED_OUTであること");

        // === DBの検証: clock_entriesに4件の打刻ログがあること ===
        Integer clockEntryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clock_entries WHERE attendance_id IN " +
                        "(SELECT id FROM attendances WHERE employee_id = ?)",
                Integer.class,
                EMPLOYEE_FULL_FLOW
        );
        assertEquals(4, clockEntryCount,
                "DB検証: clock_entriesに4件（出勤・休憩開始・休憩終了・退勤）の打刻ログがあること");
    }

    // ========================================
    // テスト3: 出勤なしで退勤打刻 → 404エラー
    // ========================================

    @Test
    @Order(3)
    @DisplayName("出勤打刻なしで退勤打刻すると404エラーが返る")
    void clockOut_withoutClockIn_shouldReturn404() {
        // 出勤打刻をせずに直接退勤打刻リクエストを送信する
        Map<String, Object> request = Map.of(
                "employeeId", EMPLOYEE_NO_CLOCK_IN.toString(),
                "clockTime", "2026-02-23T18:00:00+09:00",
                "source", "WEB"
        );

        // POST /api/v1/attendances/clock-out を実行する
        ResponseEntity<ProblemDetail> response = restTemplate.postForEntity(
                BASE_PATH + "/clock-out",
                request,
                ProblemDetail.class
        );

        // HTTPステータス404（Not Found）が返ることを検証する
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "出勤記録がない状態での退勤打刻は404エラーになること");

        // ProblemDetailのタイトルを検証する
        ProblemDetail problemDetail = response.getBody();
        assertNotNull(problemDetail, "ProblemDetailレスポンスがnullでないこと");
        assertEquals("Not Found", problemDetail.getTitle(),
                "ProblemDetailのタイトルがNot Foundであること");
    }

    // ========================================
    // テスト4: バリデーションエラー（employeeId未指定） → 400エラー
    // ========================================

    @Test
    @Order(4)
    @DisplayName("employeeIdが未指定の場合、400バリデーションエラーが返る")
    void clockIn_withMissingEmployeeId_shouldReturn400() {
        // employeeIdをnullにしたリクエストを送信する
        // Map.ofはnull値を許容しないため、HashMapを使う
        java.util.HashMap<String, Object> request = new java.util.HashMap<>();
        request.put("employeeId", null);
        request.put("clockTime", "2026-02-23T09:00:00+09:00");
        request.put("source", "WEB");

        // POST /api/v1/attendances/clock-in を実行する
        ResponseEntity<ProblemDetail> response = restTemplate.postForEntity(
                BASE_PATH + "/clock-in",
                request,
                ProblemDetail.class
        );

        // HTTPステータス400（Bad Request）が返ることを検証する
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "employeeId未指定の場合は400バリデーションエラーが返ること");

        // ProblemDetailの内容を検証する
        ProblemDetail problemDetail = response.getBody();
        assertNotNull(problemDetail, "ProblemDetailレスポンスがnullでないこと");
    }

    // ========================================
    // テスト5: 二重出勤 → 409 Conflictエラー
    // ========================================

    @Test
    @Order(5)
    @DisplayName("出勤打刻済みの従業員が再度出勤打刻すると409エラーが返る")
    void clockIn_twice_shouldReturn409() {
        // 1回目の出勤打刻リクエスト（正常に成功するはず）
        Map<String, Object> firstRequest = Map.of(
                "employeeId", EMPLOYEE_DOUBLE_CLOCK_IN.toString(),
                "clockTime", "2026-02-23T09:00:00+09:00",
                "source", "WEB"
        );

        ResponseEntity<ClockInResponse> firstResponse = restTemplate.postForEntity(
                BASE_PATH + "/clock-in",
                firstRequest,
                ClockInResponse.class
        );

        // 1回目の出勤打刻が成功することを検証する
        assertEquals(HttpStatus.OK, firstResponse.getStatusCode(),
                "1回目の出勤打刻が成功すること");

        // 2回目の出勤打刻リクエスト（同一従業員・同一日付で再度出勤 → 状態遷移違反）
        Map<String, Object> secondRequest = Map.of(
                "employeeId", EMPLOYEE_DOUBLE_CLOCK_IN.toString(),
                "clockTime", "2026-02-23T09:30:00+09:00",
                "source", "WEB"
        );

        ResponseEntity<ProblemDetail> secondResponse = restTemplate.postForEntity(
                BASE_PATH + "/clock-in",
                secondRequest,
                ProblemDetail.class
        );

        // HTTPステータス409（Conflict）が返ることを検証する
        assertEquals(HttpStatus.CONFLICT, secondResponse.getStatusCode(),
                "二重出勤打刻は409 Conflictエラーになること");

        // ProblemDetailの内容を検証する
        ProblemDetail problemDetail = secondResponse.getBody();
        assertNotNull(problemDetail, "ProblemDetailレスポンスがnullでないこと");
        assertEquals("Conflict", problemDetail.getTitle(),
                "ProblemDetailのタイトルがConflictであること");
    }
}
