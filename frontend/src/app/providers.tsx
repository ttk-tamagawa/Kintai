"use client";

import type { ReactNode } from "react";
import { AuthProvider } from "@/contexts/AuthContext";
import { MainLayout } from "@/components/layout/MainLayout";

// ========================================
// プロバイダー
// クライアントサイドのContext Provider群と
// メインレイアウトをまとめるコンポーネント
// ========================================

export function Providers({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <MainLayout>{children}</MainLayout>
    </AuthProvider>
  );
}
