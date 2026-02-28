"use client";

import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { AuthProvider } from "@/contexts/AuthContext";
import { MainLayout } from "@/components/layout/MainLayout";

// ========================================
// プロバイダー
// クライアントサイドのContext Provider群と
// メインレイアウトをまとめるコンポーネント
// /login パスではMainLayoutをスキップする
// ========================================

export function Providers({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  // ログインページではMainLayoutを表示しない
  const isLoginPage = pathname === "/login";

  return (
    <AuthProvider>
      {isLoginPage ? children : <MainLayout>{children}</MainLayout>}
    </AuthProvider>
  );
}
