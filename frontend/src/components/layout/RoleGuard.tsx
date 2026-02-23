"use client";

import type { ReactNode } from "react";
import type { Role } from "@/types";
import { useAuth } from "@/contexts/AuthContext";

// ========================================
// ロールベースガード
// 指定されたロールを持つユーザーのみコンテンツを表示する
// ========================================

interface RoleGuardProps {
  /** 表示を許可するロール一覧 */
  allowedRoles: Role[];
  /** ロールを満たす場合に表示するコンテンツ */
  children: ReactNode;
  /** ロールを満たさない場合に表示する代替コンテンツ（省略時は非表示） */
  fallback?: ReactNode;
}

export function RoleGuard({
  allowedRoles,
  children,
  fallback = null,
}: RoleGuardProps) {
  const { hasAnyRole, isAuthenticated } = useAuth();

  // 未認証、またはロールを満たさない場合は代替コンテンツを表示
  if (!isAuthenticated || !hasAnyRole(allowedRoles)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
