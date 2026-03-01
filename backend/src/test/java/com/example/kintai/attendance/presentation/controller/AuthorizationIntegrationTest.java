package com.example.kintai.attendance.presentation.controller;

import com.example.kintai.shared.infrastructure.AuthenticatedUser;
import com.example.kintai.shared.infrastructure.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 認可テスト — ロール別アクセス制御の検証
 *
 * <p>全APIエンドポイントに対して以下を検証する:
 * <ul>
 *   <li>認証なし（JWTなし）→ 401 Unauthorized</li>
 *   <li>権限不足のロール → 403 Forbidden</li>
 *   <li>正しいロール → 認可を通過（403以外のレスポンス）</li>
 * </ul>
 * </p>
 *
 * <p>このテストはビジネスロジックの正否ではなく、
 * Spring Securityの認可制御が正しく機能しているかを検証する。
 * そのため、リクエストボディの内容が不正でも403以外（400等）であれば認可は通過と判定する。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@DisplayName("認可テスト — ロール別アクセス制御")
class AuthorizationIntegrationTest {

    /** HTTPリクエスト送信用のテストクライアント */
    @Autowired
    private TestRestTemplate restTemplate;

    /** JWT トークン生成用 */
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * 各テスト実行前に共有TestRestTemplateのインターセプターをクリアする
     *
     * <p>他のテストクラスが設定したインターセプター（全ロール付きJWT）が
     * 残っていると、認可テストのヘッダーが上書きされてしまうため。</p>
     */
    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().getInterceptors().clear();
    }

    // ========================================
    // テスト用ダミーリクエストボディ
    // 認可チェック通過確認が目的のため、最小限のデータを使用する
    // ========================================

    /** 打刻リクエストのダミーボディ */
    private static final Map<String, Object> CLOCK_REQUEST = Map.of(
            "employeeId", "00000000-0000-0000-0000-000000000001",
            "clockTime", "2026-02-28T09:00:00+09:00",
            "source", "WEB"
    );

    /** 打刻修正リクエストのダミーボディ（CorrectClockRequest に合わせる） */
    private static final Map<String, Object> CORRECT_REQUEST = Map.of(
            "attendanceId", "00000000-0000-0000-0000-000000000001",
            "targetType", "CLOCK_IN",
            "correctedTime", "2026-02-28T08:55:00+09:00",
            "reason", "打刻忘れ修正",
            "approvalId", "00000000-0000-0000-0000-000000000001"
    );

    /** 手動登録リクエストのダミーボディ（RegisterManualAttendanceRequest に合わせる） */
    private static final Map<String, Object> REGISTER_REQUEST = Map.of(
            "employeeId", "00000000-0000-0000-0000-000000000001",
            "workDate", "2026-02-28",
            "startTime", "2026-02-28T09:00:00+09:00",
            "endTime", "2026-02-28T18:00:00+09:00",
            "type", "通常勤務",
            "reason", "テスト手動登録",
            "approvalId", "00000000-0000-0000-0000-000000000001"
    );

    /** 確定リクエストのダミーボディ（FinalizeRequest に合わせる） */
    private static final Map<String, Object> FINALIZE_REQUEST = Map.of(
            "attendanceId", "00000000-0000-0000-0000-000000000001",
            "monthlyClosingId", "monthly-2026-02"
    );

    /** シフトパターン作成リクエストのダミーボディ */
    private static final Map<String, Object> PATTERN_REQUEST = Map.of(
            "name", "テストパターン",
            "startTime", "09:00",
            "endTime", "18:00",
            "breakMinutes", 60,
            "isOvernight", false
    );

    /** 週次スケジュール作成リクエストのダミーボディ（AssignScheduleRequest に合わせる） */
    private static final Map<String, Object> SCHEDULE_REQUEST = Map.of(
            "employeeId", "00000000-0000-0000-0000-000000000001",
            "weekStartDate", "2026-03-02",
            "assignments", Map.of("MONDAY", "00000000-0000-0000-0000-000000000001")
    );

    /** 週次スケジュール変更リクエストのダミーボディ（ChangeScheduleRequest に合わせる） */
    private static final Map<String, Object> CHANGE_SCHEDULE_REQUEST = Map.of(
            "assignments", Map.of("MONDAY", "00000000-0000-0000-0000-000000000001")
    );

    // ========================================
    // ヘルパーメソッド
    // ========================================

    /**
     * 指定ロールのJWTトークンを含むHTTPヘッダーを生成する
     *
     * @param roles 付与するロール一覧
     * @return Authorization ヘッダー付きの HttpHeaders
     */
    private HttpHeaders headersWithRoles(String... roles) {
        String token = jwtTokenProvider.generateToken(new AuthenticatedUser(
                "test@example.com", "emp-test", "dept-001", "テスト部", "テスト太郎",
                List.of(roles)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * 指定のemployeeId・departmentId・ロールでJWTトークンを含むHTTPヘッダーを生成する
     *
     * <p>自己データアクセス制御のテスト用。リクエストの employeeId と JWT の employeeId を
     * 一致/不一致にすることで、データレベルの認可を検証する。</p>
     *
     * @param employeeId   JWTに含める従業員ID
     * @param departmentId JWTに含める部門ID
     * @param roles        付与するロール一覧
     * @return Authorization ヘッダー付きの HttpHeaders
     */
    private HttpHeaders headersWithRolesAndEmployee(String employeeId, String departmentId, String... roles) {
        String token = jwtTokenProvider.generateToken(new AuthenticatedUser(
                "test@example.com", employeeId, departmentId, "テスト部", "テスト太郎",
                List.of(roles)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * 認証なし（JWTなし）のHTTPヘッダーを生成する
     */
    private HttpHeaders headersWithoutAuth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * POSTリクエストを送信してステータスコードを返す
     */
    private HttpStatusCode post(String url, Object body, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class).getStatusCode();
    }

    /**
     * GETリクエストを送信してステータスコードを返す
     */
    private HttpStatusCode get(String url, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(null, headers), String.class).getStatusCode();
    }

    /**
     * PUTリクエストを送信してステータスコードを返す
     */
    private HttpStatusCode put(String url, Object body, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.PUT,
                new HttpEntity<>(body, headers), String.class).getStatusCode();
    }

    // ========================================
    // 認証なしテスト（JWT未付与 → 401）
    // ========================================

    @Nested
    @DisplayName("認証なし — JWTトークンなしで401が返る")
    class UnauthenticatedTests {

        @Test
        @DisplayName("POST /clock-in — 認証なし → 401")
        void clockIn_noAuth_returns401() {
            assertEquals(HttpStatus.UNAUTHORIZED,
                    post("/api/v1/attendances/clock-in", CLOCK_REQUEST, headersWithoutAuth()));
        }

        @Test
        @DisplayName("GET /today — 認証なし → 401")
        void today_noAuth_returns401() {
            assertEquals(HttpStatus.UNAUTHORIZED,
                    get("/api/v1/attendances/today?employeeId=00000000-0000-0000-0000-000000000001", headersWithoutAuth()));
        }

        @Test
        @DisplayName("POST /internal/correct — 認証なし → 401")
        void correct_noAuth_returns401() {
            assertEquals(HttpStatus.UNAUTHORIZED,
                    post("/api/v1/internal/attendances/correct", CORRECT_REQUEST, headersWithoutAuth()));
        }

        @Test
        @DisplayName("GET /shifts/patterns — 認証なし → 401")
        void patterns_noAuth_returns401() {
            assertEquals(HttpStatus.UNAUTHORIZED,
                    get("/api/v1/shifts/patterns", headersWithoutAuth()));
        }

        @Test
        @DisplayName("GET /shifts/schedules — 認証なし → 401")
        void schedules_noAuth_returns401() {
            assertEquals(HttpStatus.UNAUTHORIZED,
                    get("/api/v1/shifts/schedules?employeeId=00000000-0000-0000-0000-000000000001", headersWithoutAuth()));
        }
    }

    // ========================================
    // 勤怠コマンドAPI — hasRole('EMPLOYEE')
    // ========================================

    @Nested
    @DisplayName("勤怠コマンドAPI — EMPLOYEE ロール必須")
    class AttendanceCommandAuthTests {

        @Test
        @DisplayName("POST /clock-in — EMPLOYEE → 認可通過（403以外）")
        void clockIn_employee_allowed() {
            // JWTのemployeeIdとリクエストのemployeeIdを一致させる（自己データアクセス制御対応）
            HttpStatusCode status = post("/api/v1/attendances/clock-in",
                    CLOCK_REQUEST, headersWithRolesAndEmployee(
                            "00000000-0000-0000-0000-000000000001", "dept-001", "EMPLOYEE"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("POST /clock-in — MANAGERのみ → 403")
        void clockIn_managerOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/attendances/clock-in", CLOCK_REQUEST, headersWithRoles("MANAGER")));
        }

        @Test
        @DisplayName("POST /clock-in — HRのみ → 403")
        void clockIn_hrOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/attendances/clock-in", CLOCK_REQUEST, headersWithRoles("HR")));
        }

        @Test
        @DisplayName("POST /clock-in — ADMINのみ → 403")
        void clockIn_adminOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/attendances/clock-in", CLOCK_REQUEST, headersWithRoles("ADMIN")));
        }

        @Test
        @DisplayName("POST /clock-out — EMPLOYEE → 認可通過")
        void clockOut_employee_allowed() {
            // JWTのemployeeIdとリクエストのemployeeIdを一致させる（自己データアクセス制御対応）
            HttpStatusCode status = post("/api/v1/attendances/clock-out",
                    CLOCK_REQUEST, headersWithRolesAndEmployee(
                            "00000000-0000-0000-0000-000000000001", "dept-001", "EMPLOYEE"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("POST /clock-out — HRのみ → 403")
        void clockOut_hrOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/attendances/clock-out", CLOCK_REQUEST, headersWithRoles("HR")));
        }

        @Test
        @DisplayName("POST /break-start — EMPLOYEE → 認可通過")
        void breakStart_employee_allowed() {
            // JWTのemployeeIdとリクエストのemployeeIdを一致させる（自己データアクセス制御対応）
            HttpStatusCode status = post("/api/v1/attendances/break-start",
                    CLOCK_REQUEST, headersWithRolesAndEmployee(
                            "00000000-0000-0000-0000-000000000001", "dept-001", "EMPLOYEE"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("POST /break-start — ADMINのみ → 403")
        void breakStart_adminOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/attendances/break-start", CLOCK_REQUEST, headersWithRoles("ADMIN")));
        }

        @Test
        @DisplayName("POST /break-end — EMPLOYEE → 認可通過")
        void breakEnd_employee_allowed() {
            // JWTのemployeeIdとリクエストのemployeeIdを一致させる（自己データアクセス制御対応）
            HttpStatusCode status = post("/api/v1/attendances/break-end",
                    CLOCK_REQUEST, headersWithRolesAndEmployee(
                            "00000000-0000-0000-0000-000000000001", "dept-001", "EMPLOYEE"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("POST /break-end — MANAGERのみ → 403")
        void breakEnd_managerOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/attendances/break-end", CLOCK_REQUEST, headersWithRoles("MANAGER")));
        }
    }

    // ========================================
    // 勤怠クエリAPI — ロール別アクセス制御
    // ========================================

    @Nested
    @DisplayName("勤怠クエリAPI — ロール別制御")
    class AttendanceQueryAuthTests {

        // --- GET /today — hasRole('EMPLOYEE') ---

        @Test
        @DisplayName("GET /today — EMPLOYEE → 認可通過")
        void today_employee_allowed() {
            // JWTのemployeeIdとURLのemployeeIdを一致させる（自己データアクセス制御対応）
            HttpStatusCode status = get(
                    "/api/v1/attendances/today?employeeId=00000000-0000-0000-0000-000000000001",
                    headersWithRolesAndEmployee(
                            "00000000-0000-0000-0000-000000000001", "dept-001", "EMPLOYEE"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /today — MANAGERのみ → 403")
        void today_managerOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/attendances/today?employeeId=00000000-0000-0000-0000-000000000001",
                    headersWithRoles("MANAGER")));
        }

        // --- GET /daily — hasAnyRole('EMPLOYEE', 'MANAGER', 'HR') ---

        @Test
        @DisplayName("GET /daily — EMPLOYEE → 認可通過")
        void daily_employee_allowed() {
            // JWTのemployeeIdとURLのemployeeIdを一致させる（自己データアクセス制御対応）
            HttpStatusCode status = get(
                    "/api/v1/attendances/daily?employeeId=00000000-0000-0000-0000-000000000001&date=2026-02-28",
                    headersWithRolesAndEmployee(
                            "00000000-0000-0000-0000-000000000001", "dept-001", "EMPLOYEE"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /daily — MANAGER → 認可通過")
        void daily_manager_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/attendances/daily?employeeId=00000000-0000-0000-0000-000000000001&date=2026-02-28",
                    headersWithRoles("MANAGER"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /daily — HR → 認可通過")
        void daily_hr_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/attendances/daily?employeeId=00000000-0000-0000-0000-000000000001&date=2026-02-28",
                    headersWithRoles("HR"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /daily — ADMINのみ → 403")
        void daily_adminOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/attendances/daily?employeeId=00000000-0000-0000-0000-000000000001&date=2026-02-28",
                    headersWithRoles("ADMIN")));
        }

        // --- GET /monthly-summary — hasAnyRole('MANAGER', 'HR') ---

        @Test
        @DisplayName("GET /monthly-summary — MANAGER → 認可通過")
        void monthlySummary_manager_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/attendances/monthly-summary?departmentId=dept-001&yearMonth=2026-02",
                    headersWithRoles("MANAGER"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /monthly-summary — HR → 認可通過")
        void monthlySummary_hr_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/attendances/monthly-summary?departmentId=dept-001&yearMonth=2026-02",
                    headersWithRoles("HR"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /monthly-summary — EMPLOYEEのみ → 403")
        void monthlySummary_employeeOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/attendances/monthly-summary?departmentId=dept-001&yearMonth=2026-02",
                    headersWithRoles("EMPLOYEE")));
        }

        // --- GET /monthly-summary/export — hasAnyRole('MANAGER', 'HR') ---

        @Test
        @DisplayName("GET /monthly-summary/export — HR → 認可通過")
        void monthlySummaryExport_hr_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/attendances/monthly-summary/export?departmentId=dept-001&yearMonth=2026-02",
                    headersWithRoles("HR"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /monthly-summary/export — EMPLOYEEのみ → 403")
        void monthlySummaryExport_employeeOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/attendances/monthly-summary/export?departmentId=dept-001&yearMonth=2026-02",
                    headersWithRoles("EMPLOYEE")));
        }

        // --- GET /department-dashboard — hasAnyRole('HR', 'ADMIN') ---

        @Test
        @DisplayName("GET /department-dashboard — HR → 認可通過")
        void departmentDashboard_hr_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/attendances/department-dashboard?yearMonth=2026-02",
                    headersWithRoles("HR"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /department-dashboard — ADMIN → 認可通過")
        void departmentDashboard_admin_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/attendances/department-dashboard?yearMonth=2026-02",
                    headersWithRoles("ADMIN"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /department-dashboard — EMPLOYEEのみ → 403")
        void departmentDashboard_employeeOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/attendances/department-dashboard?yearMonth=2026-02",
                    headersWithRoles("EMPLOYEE")));
        }

        @Test
        @DisplayName("GET /department-dashboard — MANAGERのみ → 403")
        void departmentDashboard_managerOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/attendances/department-dashboard?yearMonth=2026-02",
                    headersWithRoles("MANAGER")));
        }

        // --- GET /department-dashboard/export — hasAnyRole('HR', 'ADMIN') ---

        @Test
        @DisplayName("GET /department-dashboard/export — ADMIN → 認可通過")
        void departmentDashboardExport_admin_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/attendances/department-dashboard/export?yearMonth=2026-02",
                    headersWithRoles("ADMIN"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /department-dashboard/export — MANAGERのみ → 403")
        void departmentDashboardExport_managerOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/attendances/department-dashboard/export?yearMonth=2026-02",
                    headersWithRoles("MANAGER")));
        }
    }

    // ========================================
    // 内部API — hasRole('ADMIN')
    // ========================================

    @Nested
    @DisplayName("内部API — ADMIN ロール必須")
    class InternalApiAuthTests {

        @Test
        @DisplayName("POST /correct — ADMIN → 認可通過")
        void correct_admin_allowed() {
            HttpStatusCode status = post("/api/v1/internal/attendances/correct",
                    CORRECT_REQUEST, headersWithRoles("ADMIN"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("POST /correct — EMPLOYEEのみ → 403")
        void correct_employeeOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/internal/attendances/correct", CORRECT_REQUEST, headersWithRoles("EMPLOYEE")));
        }

        @Test
        @DisplayName("POST /correct — MANAGERのみ → 403")
        void correct_managerOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/internal/attendances/correct", CORRECT_REQUEST, headersWithRoles("MANAGER")));
        }

        @Test
        @DisplayName("POST /correct — HRのみ → 403")
        void correct_hrOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/internal/attendances/correct", CORRECT_REQUEST, headersWithRoles("HR")));
        }

        @Test
        @DisplayName("POST /register — ADMIN → 認可通過")
        void register_admin_allowed() {
            HttpStatusCode status = post("/api/v1/internal/attendances/register",
                    REGISTER_REQUEST, headersWithRoles("ADMIN"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("POST /register — EMPLOYEEのみ → 403")
        void register_employeeOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/internal/attendances/register", REGISTER_REQUEST, headersWithRoles("EMPLOYEE")));
        }

        @Test
        @DisplayName("POST /finalize — ADMIN → 認可通過")
        void finalize_admin_allowed() {
            HttpStatusCode status = post("/api/v1/internal/attendances/finalize",
                    FINALIZE_REQUEST, headersWithRoles("ADMIN"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("POST /finalize — MANAGERのみ → 403")
        void finalize_managerOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/internal/attendances/finalize", FINALIZE_REQUEST, headersWithRoles("MANAGER")));
        }
    }

    // ========================================
    // シフトパターンAPI — HR(書込) / MANAGER+HR(読取)
    // ========================================

    @Nested
    @DisplayName("シフトパターンAPI — ロール別制御")
    class ShiftPatternAuthTests {

        // --- POST /patterns — hasRole('HR') ---

        @Test
        @DisplayName("POST /patterns — HR → 認可通過")
        void createPattern_hr_allowed() {
            HttpStatusCode status = post("/api/v1/shifts/patterns",
                    PATTERN_REQUEST, headersWithRoles("HR"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("POST /patterns — EMPLOYEEのみ → 403")
        void createPattern_employeeOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/shifts/patterns", PATTERN_REQUEST, headersWithRoles("EMPLOYEE")));
        }

        @Test
        @DisplayName("POST /patterns — MANAGERのみ → 403")
        void createPattern_managerOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/shifts/patterns", PATTERN_REQUEST, headersWithRoles("MANAGER")));
        }

        // --- GET /patterns — hasAnyRole('MANAGER', 'HR') ---

        @Test
        @DisplayName("GET /patterns — MANAGER → 認可通過")
        void getPatterns_manager_allowed() {
            HttpStatusCode status = get("/api/v1/shifts/patterns",
                    headersWithRoles("MANAGER"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /patterns — HR → 認可通過")
        void getPatterns_hr_allowed() {
            HttpStatusCode status = get("/api/v1/shifts/patterns",
                    headersWithRoles("HR"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /patterns — EMPLOYEEのみ → 403")
        void getPatterns_employeeOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    get("/api/v1/shifts/patterns", headersWithRoles("EMPLOYEE")));
        }

        // --- GET /patterns/{id} — hasAnyRole('MANAGER', 'HR') ---

        @Test
        @DisplayName("GET /patterns/{id} — MANAGER → 認可通過")
        void getPattern_manager_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/shifts/patterns/00000000-0000-0000-0000-000000000001",
                    headersWithRoles("MANAGER"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /patterns/{id} — EMPLOYEEのみ → 403")
        void getPattern_employeeOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/shifts/patterns/00000000-0000-0000-0000-000000000001",
                    headersWithRoles("EMPLOYEE")));
        }

        // --- POST /patterns/{id}/actions/deactivate — hasRole('HR') ---

        @Test
        @DisplayName("POST /deactivate — HR → 認可通過")
        void deactivate_hr_allowed() {
            HttpStatusCode status = post(
                    "/api/v1/shifts/patterns/00000000-0000-0000-0000-000000000001/actions/deactivate",
                    Map.of(), headersWithRoles("HR"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("POST /deactivate — MANAGERのみ → 403")
        void deactivate_managerOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, post(
                    "/api/v1/shifts/patterns/00000000-0000-0000-0000-000000000001/actions/deactivate",
                    Map.of(), headersWithRoles("MANAGER")));
        }

        // --- POST /patterns/{id}/actions/reactivate — hasRole('HR') ---

        @Test
        @DisplayName("POST /reactivate — HR → 認可通過")
        void reactivate_hr_allowed() {
            HttpStatusCode status = post(
                    "/api/v1/shifts/patterns/00000000-0000-0000-0000-000000000001/actions/reactivate",
                    Map.of(), headersWithRoles("HR"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("POST /reactivate — EMPLOYEEのみ → 403")
        void reactivate_employeeOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, post(
                    "/api/v1/shifts/patterns/00000000-0000-0000-0000-000000000001/actions/reactivate",
                    Map.of(), headersWithRoles("EMPLOYEE")));
        }
    }

    // ========================================
    // 週次スケジュールAPI — MANAGER(書込) / EMPLOYEE+MANAGER(読取)
    // ========================================

    @Nested
    @DisplayName("週次スケジュールAPI — ロール別制御")
    class WeeklyScheduleAuthTests {

        // --- POST /schedules — hasRole('MANAGER') ---

        @Test
        @DisplayName("POST /schedules — MANAGER → 認可通過")
        void assignSchedule_manager_allowed() {
            HttpStatusCode status = post("/api/v1/shifts/schedules",
                    SCHEDULE_REQUEST, headersWithRoles("MANAGER"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("POST /schedules — EMPLOYEEのみ → 403")
        void assignSchedule_employeeOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/shifts/schedules", SCHEDULE_REQUEST, headersWithRoles("EMPLOYEE")));
        }

        @Test
        @DisplayName("POST /schedules — HRのみ → 403")
        void assignSchedule_hrOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/shifts/schedules", SCHEDULE_REQUEST, headersWithRoles("HR")));
        }

        // --- PUT /schedules/{id} — hasRole('MANAGER') ---

        @Test
        @DisplayName("PUT /schedules/{id} — MANAGER → 認可通過")
        void changeSchedule_manager_allowed() {
            HttpStatusCode status = put(
                    "/api/v1/shifts/schedules/00000000-0000-0000-0000-000000000001",
                    CHANGE_SCHEDULE_REQUEST, headersWithRoles("MANAGER"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("PUT /schedules/{id} — EMPLOYEEのみ → 403")
        void changeSchedule_employeeOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, put(
                    "/api/v1/shifts/schedules/00000000-0000-0000-0000-000000000001",
                    CHANGE_SCHEDULE_REQUEST, headersWithRoles("EMPLOYEE")));
        }

        // --- POST /schedules/{id}/actions/publish — hasRole('MANAGER') ---

        @Test
        @DisplayName("POST /publish — MANAGER → 認可通過")
        void publishSchedule_manager_allowed() {
            HttpStatusCode status = post(
                    "/api/v1/shifts/schedules/00000000-0000-0000-0000-000000000001/actions/publish",
                    Map.of(), headersWithRoles("MANAGER"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("POST /publish — HRのみ → 403")
        void publishSchedule_hrOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, post(
                    "/api/v1/shifts/schedules/00000000-0000-0000-0000-000000000001/actions/publish",
                    Map.of(), headersWithRoles("HR")));
        }

        // --- GET /schedules — hasAnyRole('EMPLOYEE', 'MANAGER') ---

        @Test
        @DisplayName("GET /schedules — EMPLOYEE → 認可通過")
        void getSchedules_employee_allowed() {
            // JWTのemployeeIdとURLのemployeeIdを一致させる（自己データアクセス制御対応）
            HttpStatusCode status = get("/api/v1/shifts/schedules?employeeId=00000000-0000-0000-0000-000000000001",
                    headersWithRolesAndEmployee(
                            "00000000-0000-0000-0000-000000000001", "dept-001", "EMPLOYEE"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /schedules — MANAGER → 認可通過")
        void getSchedules_manager_allowed() {
            HttpStatusCode status = get("/api/v1/shifts/schedules?employeeId=00000000-0000-0000-0000-000000000001",
                    headersWithRoles("MANAGER"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /schedules — HRのみ → 403")
        void getSchedules_hrOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    get("/api/v1/shifts/schedules?employeeId=00000000-0000-0000-0000-000000000001", headersWithRoles("HR")));
        }

        // --- GET /schedules/{id} — hasAnyRole('EMPLOYEE', 'MANAGER') ---

        @Test
        @DisplayName("GET /schedules/{id} — EMPLOYEE → 認可通過")
        void getSchedule_employee_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/shifts/schedules/00000000-0000-0000-0000-000000000001",
                    headersWithRoles("EMPLOYEE"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /schedules/{id} — HRのみ → 403")
        void getSchedule_hrOnly_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/shifts/schedules/00000000-0000-0000-0000-000000000001",
                    headersWithRoles("HR")));
        }
    }

    // ========================================
    // 自己データアクセス制御テスト — EMPLOYEE/MANAGERのデータレベル認可
    // ========================================

    @Nested
    @DisplayName("自己データアクセス制御 — データレベルの認可検証")
    class SelfDataAccessTests {

        /** テスト用の共通employeeId（リクエストで使用するID） */
        private static final String TARGET_EMPLOYEE_ID = "00000000-0000-0000-0000-000000000001";
        /** テスト用の別employeeId（JWT側に設定して不一致を作る） */
        private static final String OTHER_EMPLOYEE_ID = "00000000-0000-0000-0000-000000000099";

        // --- EMPLOYEE: 他人のemployeeIdで打刻 → 403 ---

        @Test
        @DisplayName("POST /clock-in — EMPLOYEE が他人のemployeeIdで打刻 → 403")
        void clockIn_employee_otherEmployeeId_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/attendances/clock-in", CLOCK_REQUEST,
                            headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "EMPLOYEE")));
        }

        @Test
        @DisplayName("POST /clock-out — EMPLOYEE が他人のemployeeIdで打刻 → 403")
        void clockOut_employee_otherEmployeeId_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/attendances/clock-out", CLOCK_REQUEST,
                            headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "EMPLOYEE")));
        }

        @Test
        @DisplayName("POST /break-start — EMPLOYEE が他人のemployeeIdで打刻 → 403")
        void breakStart_employee_otherEmployeeId_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/attendances/break-start", CLOCK_REQUEST,
                            headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "EMPLOYEE")));
        }

        @Test
        @DisplayName("POST /break-end — EMPLOYEE が他人のemployeeIdで打刻 → 403")
        void breakEnd_employee_otherEmployeeId_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN,
                    post("/api/v1/attendances/break-end", CLOCK_REQUEST,
                            headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "EMPLOYEE")));
        }

        // --- EMPLOYEE: 他人のemployeeIdでクエリ → 403 ---

        @Test
        @DisplayName("GET /today — EMPLOYEE が他人のemployeeIdで取得 → 403")
        void today_employee_otherEmployeeId_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/attendances/today?employeeId=" + TARGET_EMPLOYEE_ID,
                    headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "EMPLOYEE")));
        }

        @Test
        @DisplayName("GET /daily — EMPLOYEE が他人のemployeeIdで取得 → 403")
        void daily_employee_otherEmployeeId_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/attendances/daily?employeeId=" + TARGET_EMPLOYEE_ID,
                    headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "EMPLOYEE")));
        }

        @Test
        @DisplayName("GET /schedules — EMPLOYEE が他人のemployeeIdで取得 → 403")
        void getSchedules_employee_otherEmployeeId_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/shifts/schedules?employeeId=" + TARGET_EMPLOYEE_ID,
                    headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "EMPLOYEE")));
        }

        // --- MANAGER: 他人のemployeeIdでクエリ → 認可通過（MANAGERはemployeeId制限なし） ---

        @Test
        @DisplayName("GET /daily — MANAGER が他人のemployeeIdで取得 → 認可通過")
        void daily_manager_otherEmployeeId_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/attendances/daily?employeeId=" + TARGET_EMPLOYEE_ID,
                    headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "MANAGER"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        @Test
        @DisplayName("GET /schedules — MANAGER が他人のemployeeIdで取得 → 認可通過")
        void getSchedules_manager_otherEmployeeId_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/shifts/schedules?employeeId=" + TARGET_EMPLOYEE_ID,
                    headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "MANAGER"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        // --- MANAGER: 他部署のdepartmentIdで月次サマリー → 403 ---

        @Test
        @DisplayName("GET /monthly-summary — MANAGER が他部署のdepartmentIdで取得 → 403")
        void monthlySummary_manager_otherDepartment_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/attendances/monthly-summary?departmentId=dept-999",
                    headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "MANAGER")));
        }

        @Test
        @DisplayName("GET /monthly-summary/export — MANAGER が他部署のdepartmentIdでエクスポート → 403")
        void monthlySummaryExport_manager_otherDepartment_forbidden() {
            assertEquals(HttpStatus.FORBIDDEN, get(
                    "/api/v1/attendances/monthly-summary/export?departmentId=dept-999",
                    headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "MANAGER")));
        }

        // --- MANAGER: 自部署のdepartmentIdで月次サマリー → 認可通過 ---

        @Test
        @DisplayName("GET /monthly-summary — MANAGER が自部署のdepartmentIdで取得 → 認可通過")
        void monthlySummary_manager_ownDepartment_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/attendances/monthly-summary?departmentId=dept-001",
                    headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "MANAGER"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }

        // --- HR: 他部署のdepartmentIdで月次サマリー → 認可通過（HRは全部署OK） ---

        @Test
        @DisplayName("GET /monthly-summary — HR が他部署のdepartmentIdで取得 → 認可通過")
        void monthlySummary_hr_otherDepartment_allowed() {
            HttpStatusCode status = get(
                    "/api/v1/attendances/monthly-summary?departmentId=dept-999",
                    headersWithRolesAndEmployee(OTHER_EMPLOYEE_ID, "dept-001", "HR"));
            assertNotEquals(HttpStatus.FORBIDDEN, status);
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
        }
    }

    // ========================================
    // 認証エンドポイント — permitAll（認証不要）
    // ========================================

    @Nested
    @DisplayName("認証エンドポイント — 認証不要でアクセス可能")
    class AuthEndpointTests {

        @Test
        @DisplayName("POST /auth/dev-login — 認証なしでもアクセス可能（401/403以外）")
        void devLogin_noAuth_accessible() {
            HttpStatusCode status = post("/api/v1/auth/dev-login",
                    Map.of("email", "yamada@example.com"), headersWithoutAuth());
            assertNotEquals(HttpStatus.UNAUTHORIZED, status);
            assertNotEquals(HttpStatus.FORBIDDEN, status);
        }
    }
}
