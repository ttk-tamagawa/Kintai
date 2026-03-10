package com.example.kintai.shared.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AccessControl 単体テスト — データレベルのアクセス制御ロジックを検証する
 */
@DisplayName("AccessControl — データレベルアクセス制御")
class AccessControlTest {

    private AccessControl accessControl;

    /** テスト用の固定ID */
    private static final UUID OWN_EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final String OWN_DEPARTMENT_ID = "dept-001";
    private static final String OTHER_DEPARTMENT_ID = "dept-999";

    @BeforeEach
    void setUp() {
        accessControl = new AccessControl();
    }

    /**
     * テスト用の Authentication オブジェクトを生成するヘルパー
     */
    private Authentication authWith(String employeeId, String departmentId, String... roles) {
        AuthenticatedUser user = new AuthenticatedUser(
                "test@example.com", employeeId, departmentId, "テスト部", "テスト太郎",
                List.of(roles));
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(user, null, authorities);
    }

    // ========================================
    // canAccessEmployee テスト
    // ========================================

    @Nested
    @DisplayName("canAccessEmployee — 従業員IDベースのアクセス制御")
    class CanAccessEmployeeTests {

        @Test
        @DisplayName("EMPLOYEE — 自分のemployeeId → true")
        void employee_ownId_returnsTrue() {
            Authentication auth = authWith(OWN_EMPLOYEE_ID.toString(), OWN_DEPARTMENT_ID, "EMPLOYEE");
            assertTrue(accessControl.canAccessEmployee(auth, OWN_EMPLOYEE_ID));
        }

        @Test
        @DisplayName("EMPLOYEE — 他人のemployeeId → false")
        void employee_otherId_returnsFalse() {
            Authentication auth = authWith(OWN_EMPLOYEE_ID.toString(), OWN_DEPARTMENT_ID, "EMPLOYEE");
            assertFalse(accessControl.canAccessEmployee(auth, OTHER_EMPLOYEE_ID));
        }

        @Test
        @DisplayName("MANAGER — 他人のemployeeId → true（全員アクセス可）")
        void manager_otherId_returnsTrue() {
            Authentication auth = authWith(OWN_EMPLOYEE_ID.toString(), OWN_DEPARTMENT_ID, "MANAGER");
            assertTrue(accessControl.canAccessEmployee(auth, OTHER_EMPLOYEE_ID));
        }

        @Test
        @DisplayName("HR — 他人のemployeeId → true（全員アクセス可）")
        void hr_otherId_returnsTrue() {
            Authentication auth = authWith(OWN_EMPLOYEE_ID.toString(), OWN_DEPARTMENT_ID, "HR");
            assertTrue(accessControl.canAccessEmployee(auth, OTHER_EMPLOYEE_ID));
        }

        @Test
        @DisplayName("ADMIN — 他人のemployeeId → true（全員アクセス可）")
        void admin_otherId_returnsTrue() {
            Authentication auth = authWith(OWN_EMPLOYEE_ID.toString(), OWN_DEPARTMENT_ID, "ADMIN");
            assertTrue(accessControl.canAccessEmployee(auth, OTHER_EMPLOYEE_ID));
        }
    }

    // ========================================
    // canAccessDepartment テスト
    // ========================================

    @Nested
    @DisplayName("canAccessDepartment — 部門IDベースのアクセス制御")
    class CanAccessDepartmentTests {

        @Test
        @DisplayName("MANAGER — 自部署のdepartmentId → true")
        void manager_ownDept_returnsTrue() {
            Authentication auth = authWith(OWN_EMPLOYEE_ID.toString(), OWN_DEPARTMENT_ID, "MANAGER");
            assertTrue(accessControl.canAccessDepartment(auth, OWN_DEPARTMENT_ID));
        }

        @Test
        @DisplayName("MANAGER — 他部署のdepartmentId → false")
        void manager_otherDept_returnsFalse() {
            Authentication auth = authWith(OWN_EMPLOYEE_ID.toString(), OWN_DEPARTMENT_ID, "MANAGER");
            assertFalse(accessControl.canAccessDepartment(auth, OTHER_DEPARTMENT_ID));
        }

        @Test
        @DisplayName("HR — 他部署のdepartmentId → true（全部署アクセス可）")
        void hr_otherDept_returnsTrue() {
            Authentication auth = authWith(OWN_EMPLOYEE_ID.toString(), OWN_DEPARTMENT_ID, "HR");
            assertTrue(accessControl.canAccessDepartment(auth, OTHER_DEPARTMENT_ID));
        }

        @Test
        @DisplayName("ADMIN — 他部署のdepartmentId → true（全部署アクセス可）")
        void admin_otherDept_returnsTrue() {
            Authentication auth = authWith(OWN_EMPLOYEE_ID.toString(), OWN_DEPARTMENT_ID, "ADMIN");
            assertTrue(accessControl.canAccessDepartment(auth, OTHER_DEPARTMENT_ID));
        }
    }
}
