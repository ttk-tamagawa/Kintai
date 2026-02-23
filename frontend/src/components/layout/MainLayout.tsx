"use client";

import { useState, type ReactNode } from "react";
import { Header } from "./Header";
import { Sidebar } from "./Sidebar";

// ========================================
// メインレイアウト
// ヘッダー + サイドバー + メインコンテンツの3カラム構成
// レスポンシブ対応: Desktop(>1024px) / Tablet(768-1024px) / Mobile(<768px)
// ========================================

interface MainLayoutProps {
  children: ReactNode;
}

export function MainLayout({ children }: MainLayoutProps) {
  // モバイル時のサイドバー表示状態
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="flex min-h-screen flex-col">
      {/* ヘッダー */}
      <Header onToggleSidebar={() => setSidebarOpen(true)} />

      <div className="flex flex-1">
        {/* サイドバー */}
        <Sidebar
          isOpen={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
        />

        {/* メインコンテンツ */}
        <main className="flex-1 overflow-auto p-4 lg:p-6">
          {children}
        </main>
      </div>
    </div>
  );
}
