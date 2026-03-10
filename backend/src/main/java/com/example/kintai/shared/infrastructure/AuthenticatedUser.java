package com.example.kintai.shared.infrastructure;

import java.util.List;

/**
 * 認証済みユーザー情報 — JWTペイロードと1対1で対応するrecord
 *
 * <p>JwtAuthenticationFilter が JWT を検証した後、
 * SecurityContext に格納するユーザー情報を保持する。
 * フロントエンドの AuthContext.AuthUser と同じ構造を持つ。</p>
 */
public record AuthenticatedUser(
        String email,
        String employeeId,
        String departmentId,
        String departmentName,
        String name,
        List<String> roles
) {
}
