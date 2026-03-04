package com.example.kintai.attendance.presentation.controller;

import com.example.kintai.attendance.presentation.dto.ClockActionRequest;
import com.example.kintai.attendance.presentation.dto.DailyAttendancePageResponse;
import com.example.kintai.attendance.presentation.dto.DepartmentDashboardResponse;
import com.example.kintai.attendance.presentation.dto.MonthlySummaryResponse;
import com.example.kintai.attendance.presentation.dto.TodayAttendanceResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 勤怠記録クエリAPI統合テスト — タスク9-2-2
 *
 * <p>6つの読み取りAPIエンドポイントをHTTPレベルで検証する。
 * テスト用PostgreSQL（kintai_test）に対して実際にHTTPリクエストを発行し、
 * レスポンスのステータスコードとボディ内容を検証する。</p>
 *
 * <p>テストデータ戦略:
 * <ul>
 *   <li>GET /today — コマンドAPI（POST /clock-in）でデータを作成し、プロジェクター経由でRead Modelに反映後にクエリ</li>
 *   <li>GET /daily — JdbcTemplateでattendance_summariesに直接INSERT</li>
 *   <li>GET /monthly-summary — データなし状態で200が返ることを検証</li>
 *   <li>GET /department-dashboard — データなし状態で200が返ることを検証</li>
 * </ul>
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@DisplayName("勤怠記録クエリAPI統合テスト")
class AttendanceQueryControllerIntegrationTest {

    /** HTTPリクエスト送信用のテストクライアント */
    @Autowired
    private TestRestTemplate restTemplate;

    /** テストデータの直接投入・クリーンアップ用 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** JWT トークン生成用 */
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /** APIのベースURL */
    private static final String BASE_URL = "/api/v1/attendances";

    /**
     * 各テスト実行前にテストデータをクリーンアップする
     *
     * <p>外部キー制約の順序に従い、子テーブルから先に削除する。
     * attendance_summaries → attendance_events → clock_entries → attendances の順。</p>
     */
    @BeforeEach
    void setUp() {
        // テスト用JWTトークンを生成してリクエストインターセプターに設定する
        String token = jwtTokenProvider.generateToken(new AuthenticatedUser(
                "test@example.com", "emp-test", "dept-001", "テスト部", "テスト太郎",
                List.of("EMPLOYEE", "MANAGER", "HR", "ADMIN")));
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("Authorization", "Bearer " + token);
            return execution.execute(request, body);
        });

        // Read Modelテーブルをクリーンアップする
        jdbcTemplate.execute("DELETE FROM attendance_summaries");
        jdbcTemplate.execute("DELETE FROM monthly_attendance_summaries");

        // Write Modelテーブルをクリーンアップする（FK順序: 子→親）
        jdbcTemplate.execute("DELETE FROM attendance_events");
        jdbcTemplate.execute("DELETE FROM clock_entries");
        jdbcTemplate.execute("DELETE FROM attendances");

        // マテリアライズドビューをリフレッシュする（部門ダッシュボードテスト用）
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW department_attendance_stats");
    }

    // ========================================
    // GET /today — 当日の勤怠ステータス取得
    // ========================================

    @Nested
    @DisplayName("GET /today — 当日勤怠ステータス取得")
    class GetTodayAttendanceTest {

        @Test
        @DisplayName("勤怠記録なしの場合、204 No Contentを返す")
        void returnsNoContentWhenNoAttendance() {
            // テスト用の従業員IDを生成する（ランダムUUIDで衝突回避）
            UUID employeeId = UUID.randomUUID();

            // 当日勤怠取得APIを呼び出す
            ResponseEntity<Void> response = restTemplate.getForEntity(
                    BASE_URL + "/today?employeeId={employeeId}",
                    Void.class,
                    employeeId);

            // 勤怠記録がないので 204 No Content を期待する
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("出勤打刻後、200 OKでCLOCKED_INステータスを返す")
        void returns200WithClockedInStatusAfterClockIn() throws InterruptedException {
            // テスト用の従業員IDを生成する
            String employeeId = UUID.randomUUID().toString();

            // 出勤打刻リクエストを組み立てる（現在時刻をJSTで指定）
            OffsetDateTime clockTime = OffsetDateTime.now(ZoneOffset.ofHours(9));
            ClockActionRequest clockInRequest = new ClockActionRequest(
                    employeeId, clockTime, "WEB", null);

            // 出勤打刻APIを呼び出す（Write Model + イベント発行 → プロジェクターがRead Modelを更新）
            ResponseEntity<String> clockInResponse = restTemplate.postForEntity(
                    BASE_URL + "/clock-in", clockInRequest, String.class);
            assertThat(clockInResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // プロジェクターの非同期処理（AFTER_COMMIT + REQUIRES_NEW）を待機する
            // プロジェクターがRead Modelを更新するまでポーリングで待つ（最大5秒）
            TodayAttendanceResponse todayResponse = null;
            for (int i = 0; i < 50; i++) {
                Thread.sleep(100);
                ResponseEntity<TodayAttendanceResponse> response = restTemplate.getForEntity(
                        BASE_URL + "/today?employeeId={employeeId}",
                        TodayAttendanceResponse.class,
                        employeeId);
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    todayResponse = response.getBody();
                    break;
                }
            }

            // Read Modelにデータが反映されていることを検証する
            assertThat(todayResponse).isNotNull();
            assertThat(todayResponse.employeeId()).isEqualTo(employeeId);
            assertThat(todayResponse.workDate()).isEqualTo(LocalDate.now());
            assertThat(todayResponse.status()).isEqualTo("CLOCKED_IN");
            assertThat(todayResponse.clockIn()).isNotNull();
            // 未退勤なのでclockOutはnull
            assertThat(todayResponse.clockOut()).isNull();
        }
    }

    // ========================================
    // GET /daily — 日次勤怠一覧
    // ========================================

    @Nested
    @DisplayName("GET /daily — 日次勤怠一覧")
    class GetDailyAttendancesTest {

        @Test
        @DisplayName("データなしの場合、200 OKで空のcontentを返す")
        void returns200WithEmptyContentWhenNoData() {
            // テスト用の従業員IDを生成する
            UUID employeeId = UUID.randomUUID();
            LocalDate today = LocalDate.now();

            // 日次勤怠一覧APIを呼び出す（日付範囲を指定）
            ResponseEntity<DailyAttendancePageResponse> response = restTemplate.getForEntity(
                    BASE_URL + "/daily?employeeId={employeeId}&dateFrom={from}&dateTo={to}",
                    DailyAttendancePageResponse.class,
                    employeeId, today.withDayOfMonth(1), today);

            // 200 OKで空のページネーション結果を期待する
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().content()).isEmpty();
            assertThat(response.getBody().totalElements()).isEqualTo(0);
            assertThat(response.getBody().totalPages()).isEqualTo(0);
        }

        @Test
        @DisplayName("テストデータ投入後、ページネーション付きでデータを返す")
        void returnsPaginatedDataWithTestData() {
            // テスト用データを準備する
            UUID employeeId = UUID.randomUUID();
            LocalDate today = LocalDate.now();
            UUID attendanceId = UUID.randomUUID();
            Instant clockInTime = today.atTime(9, 0).toInstant(ZoneOffset.ofHours(9));

            // Write Model（attendances）にベースレコードを挿入する（FK制約のため必要）
            jdbcTemplate.update(
                    """
                    INSERT INTO attendances (id, employee_id, work_date, status, version, created_at, updated_at, created_by, updated_by)
                    VALUES (?, ?, ?, 'CLOCKED_IN', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'test', 'test')
                    """,
                    attendanceId, employeeId, today);

            // Read Model（attendance_summaries）にテストデータを直接挿入する
            jdbcTemplate.update(
                    """
                    INSERT INTO attendance_summaries
                        (attendance_id, employee_id, work_date, status,
                         clock_in_time, clock_out_time,
                         scheduled_minutes, actual_minutes, break_minutes, net_work_minutes,
                         regular_overtime_minutes, late_night_minutes, holiday_minutes, total_overtime_minutes,
                         last_event_at, event_count, created_at, updated_at, created_by, updated_by)
                    VALUES (?, ?, ?, 'CLOCKED_IN',
                            ?, NULL,
                            480, 120, 0, 120,
                            0, 0, 0, 0,
                            CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'test', 'test')
                    """,
                    attendanceId, employeeId, today, Timestamp.from(clockInTime));

            // 日次勤怠一覧APIを呼び出す
            ResponseEntity<DailyAttendancePageResponse> response = restTemplate.getForEntity(
                    BASE_URL + "/daily?employeeId={employeeId}&dateFrom={from}&dateTo={to}&page=0&size=10",
                    DailyAttendancePageResponse.class,
                    employeeId, today, today);

            // 200 OKで1件のデータを期待する
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            DailyAttendancePageResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.content()).hasSize(1);
            assertThat(body.totalElements()).isEqualTo(1);
            assertThat(body.totalPages()).isEqualTo(1);
            assertThat(body.page()).isEqualTo(0);
            assertThat(body.size()).isEqualTo(10);

            // データ内容を検証する
            DailyAttendancePageResponse.DailyAttendanceRow row = body.content().getFirst();
            assertThat(row.attendanceId()).isEqualTo(attendanceId);
            assertThat(row.employeeId()).isEqualTo(employeeId);
            assertThat(row.workDate()).isEqualTo(today);
            assertThat(row.status()).isEqualTo("CLOCKED_IN");
            assertThat(row.clockInTime()).isNotNull();
            assertThat(row.scheduledMinutes()).isEqualTo(480);
        }
    }

    // ========================================
    // GET /monthly-summary — 月次サマリー
    // ========================================

    @Nested
    @DisplayName("GET /monthly-summary — 月次サマリー")
    class GetMonthlySummaryTest {

        @Test
        @DisplayName("データなしの場合でも200 OKを返す（KPIはゼロ、従業員テーブルは空）")
        void returns200WithEmptyDataWhenNoRecords() {
            // シードデータの部門ID（dept-001: 営業部）を使用する
            String departmentId = "dept-001";
            int year = LocalDate.now().getYear();
            int month = LocalDate.now().getMonthValue();

            // 月次サマリーAPIを呼び出す
            ResponseEntity<MonthlySummaryResponse> response = restTemplate.getForEntity(
                    BASE_URL + "/monthly-summary?departmentId={deptId}&year={year}&month={month}",
                    MonthlySummaryResponse.class,
                    departmentId, year, month);

            // 200 OKを期待する
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            MonthlySummaryResponse body = response.getBody();
            assertThat(body).isNotNull();

            // KPIはゼロ値を期待する
            assertThat(body.kpi()).isNotNull();
            assertThat(body.kpi().totalWorkDays()).isEqualTo(0);
            assertThat(body.kpi().totalWorkHours()).isEqualTo(0.0);
            assertThat(body.kpi().totalOvertimeHours()).isEqualTo(0.0);

            // 従業員テーブルは空を期待する
            assertThat(body.employees()).isNotNull();
            assertThat(body.employees().content()).isEmpty();
            assertThat(body.employees().totalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("月次テストデータ投入後、KPIと従業員データを返す")
        void returns200WithKpiAndEmployeeData() {
            // テスト用データを準備する
            UUID employeeId = UUID.randomUUID();
            String departmentId = "dept-001";
            int year = LocalDate.now().getYear();
            int month = LocalDate.now().getMonthValue();

            // monthly_attendance_summariesにテストデータを直接挿入する
            jdbcTemplate.update(
                    """
                    INSERT INTO monthly_attendance_summaries
                        (id, employee_id, employee_name, department_id, year, month,
                         total_work_days, total_work_minutes, total_overtime_minutes,
                         total_late_night_minutes, total_holiday_minutes, total_break_minutes,
                         paid_leave_used, created_at, updated_at, created_by, updated_by)
                    VALUES (?, ?, '統合テスト 太郎', ?, ?, ?,
                            20, 9600, 1200,
                            120, 0, 1200,
                            2.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'test', 'test')
                    """,
                    UUID.randomUUID(), employeeId, departmentId, year, month);

            // 月次サマリーAPIを呼び出す
            ResponseEntity<MonthlySummaryResponse> response = restTemplate.getForEntity(
                    BASE_URL + "/monthly-summary?departmentId={deptId}&year={year}&month={month}",
                    MonthlySummaryResponse.class,
                    departmentId, year, month);

            // 200 OKを期待する
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            MonthlySummaryResponse body = response.getBody();
            assertThat(body).isNotNull();

            // KPIに集計結果が反映されていることを検証する
            assertThat(body.kpi().totalWorkDays()).isEqualTo(20);
            // 9600分 ÷ 60 = 160.0時間
            assertThat(body.kpi().totalWorkHours()).isEqualTo(160.0);
            // 1200分 ÷ 60 = 20.0時間
            assertThat(body.kpi().totalOvertimeHours()).isEqualTo(20.0);

            // 従業員テーブルに1件あることを検証する
            assertThat(body.employees().content()).hasSize(1);
            assertThat(body.employees().totalElements()).isEqualTo(1);

            // 従業員行の内容を検証する
            MonthlySummaryResponse.EmployeeRow row = body.employees().content().getFirst();
            assertThat(row.employeeId()).isEqualTo(employeeId);
            assertThat(row.employeeName()).isEqualTo("統合テスト 太郎");
            assertThat(row.workDays()).isEqualTo(20);
        }
    }

    // ========================================
    // GET /department-dashboard — 部門ダッシュボード
    // ========================================

    @Nested
    @DisplayName("GET /department-dashboard — 部門ダッシュボード")
    class GetDepartmentDashboardTest {

        @Test
        @DisplayName("データなしの場合でも200 OKを返す（KPIはゼロ、部門テーブルは空）")
        void returns200WithEmptyDataWhenNoRecords() {
            int year = LocalDate.now().getYear();
            int month = LocalDate.now().getMonthValue();

            // 部門ダッシュボードAPIを呼び出す（departmentIdなし=全部門）
            ResponseEntity<DepartmentDashboardResponse> response = restTemplate.getForEntity(
                    BASE_URL + "/department-dashboard?year={year}&month={month}",
                    DepartmentDashboardResponse.class,
                    year, month);

            // 200 OKを期待する
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            DepartmentDashboardResponse body = response.getBody();
            assertThat(body).isNotNull();

            // 当月KPIはゼロ値を期待する
            assertThat(body.kpi()).isNotNull();
            assertThat(body.kpi().avgOvertimeHours()).isEqualTo(0.0);
            assertThat(body.kpi().totalOvertimeAlertCount()).isEqualTo(0);
            assertThat(body.kpi().totalMissingClockCount()).isEqualTo(0);
            assertThat(body.kpi().avgAttendanceRate()).isEqualTo(0.0);

            // 前月KPIもゼロ値を期待する
            assertThat(body.previousMonth()).isNotNull();
            assertThat(body.previousMonth().avgOvertimeHours()).isEqualTo(0.0);

            // 部門テーブルは空を期待する
            assertThat(body.departments()).isNotNull();
            assertThat(body.departments().content()).isEmpty();
            assertThat(body.departments().totalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("部門IDフィルタを指定しても200 OKを返す")
        void returns200WithDepartmentFilter() {
            // シードデータの部門ID（dept-002: 開発部）でフィルタする
            String departmentId = "dept-002";
            int year = LocalDate.now().getYear();
            int month = LocalDate.now().getMonthValue();

            // 部門IDフィルタ付きで部門ダッシュボードAPIを呼び出す
            ResponseEntity<DepartmentDashboardResponse> response = restTemplate.getForEntity(
                    BASE_URL + "/department-dashboard?departmentId={deptId}&year={year}&month={month}",
                    DepartmentDashboardResponse.class,
                    departmentId, year, month);

            // 200 OKを期待する
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            DepartmentDashboardResponse body = response.getBody();
            assertThat(body).isNotNull();

            // KPIとテーブルが正常に返ることを検証する
            assertThat(body.kpi()).isNotNull();
            assertThat(body.previousMonth()).isNotNull();
            assertThat(body.departments()).isNotNull();
        }
    }
}
