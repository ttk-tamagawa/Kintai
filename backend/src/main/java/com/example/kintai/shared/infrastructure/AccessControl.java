package com.example.kintai.shared.infrastructure;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * アクセス制御コンポーネント — データレベルの認可チェックを提供する
 *
 * <p>SpEL式から {@code @accessControl.canAccessEmployee(...)} のように参照し、
 * {@code @PreAuthorize} のロールチェックに追加条件として組み合わせる。</p>
 *
 * <p>ルール:
 * <ul>
 *   <li>EMPLOYEE → 自分のemployeeIdのみアクセス可</li>
 *   <li>MANAGER → 自部署のdepartmentIdのみアクセス可（employeeIdは制限なし）</li>
 *   <li>HR / ADMIN → 全データにアクセス可</li>
 * </ul>
 * </p>
 */
@Component("accessControl")
public class AccessControl {

    /**
     * 従業員IDベースのアクセス制御 — EMPLOYEEは自分のみ、MANAGER/HR/ADMINは全員OK
     *
     * <p>パラメータ型はObjectで受け取る（String / UUID 両方に対応）。
     * コントローラーのクエリパラメータ（String）とリクエストDTO（UUID）の
     * いずれからも @PreAuthorize のSpEL式で呼び出せる。</p>
     *
     * @param authentication SecurityContextの認証情報
     * @param targetEmployeeId アクセス対象の従業員ID（String または UUID）
     * @return アクセス許可ならtrue
     */
    public boolean canAccessEmployee(Authentication authentication, Object targetEmployeeId) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        // MANAGER / HR / ADMIN は全従業員にアクセス可能
        if (hasAnyRole(user, "MANAGER", "HR", "ADMIN")) {
            return true;
        }

        // EMPLOYEE は自分の employeeId のみアクセス可能（toString で統一比較）
        return user.employeeId().equals(targetEmployeeId.toString());
    }

    /**
     * 部門IDベースのアクセス制御 — MANAGERは自部署のみ、HR/ADMINは全部署OK
     *
     * @param authentication SecurityContextの認証情報
     * @param targetDepartmentId アクセス対象の部門ID
     * @return アクセス許可ならtrue
     */
    public boolean canAccessDepartment(Authentication authentication, String targetDepartmentId) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        // HR / ADMIN は全部署にアクセス可能
        if (hasAnyRole(user, "HR", "ADMIN")) {
            return true;
        }

        // MANAGER は自部署のみアクセス可能
        return user.departmentId().equals(targetDepartmentId);
    }

    /**
     * ロール判定ヘルパー — ユーザーが指定ロールのいずれかを持つか判定する
     *
     * @param user 認証済みユーザー
     * @param roles チェック対象のロール一覧
     * @return いずれかのロールを持っていればtrue
     */
    private boolean hasAnyRole(AuthenticatedUser user, String... roles) {
        for (String role : roles) {
            if (user.roles().contains(role)) {
                return true;
            }
        }
        return false;
    }
}
