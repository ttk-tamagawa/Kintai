"use client";

import { useSyncExternalStore, useState, type ReactNode } from "react";
import { Header } from "./Header";
import { Sidebar } from "./Sidebar";

// ========================================
// メインレイアウト
// ヘッダー + サイドバー + メインコンテンツの3カラム構成
// レスポンシブ対応: Desktop(>1024px) / Tablet(768-1024px) / Mobile(<768px)
// SSR時はスケルトンを表示し、クライアントマウント後にRadixコンポーネントを
// 描画することでハイドレーション不一致を防止する
// ========================================

interface MainLayoutProps {
  children: ReactNode;
}

export function MainLayout({ children }: MainLayoutProps) {
  // モバイル時のサイドバー表示状態
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // SSR時はRadixコンポーネント（DropdownMenu, Sheet等）を描画しない
  // Radix内部のuseIdがSSR/クライアント間でIDずれを起こすため
  // useSyncExternalStoreで同期的にクライアント判定し、不要な再レンダーを防ぐ
  const mounted = useSyncExternalStore(
    () => () => {},
    () => true,
    () => false
  );

  // SSR時・初回ハイドレーション時はスケルトンレイアウトを表示する
  if (!mounted) {
    return (
      <div className="flex min-h-screen flex-col">
        <div className="sticky top-0 z-30 flex h-14 items-center border-b bg-background px-4 lg:px-6">
          <h1 className="text-lg font-semibold">勤怠管理システム</h1>
        </div>
        <div className="flex flex-1">
          <aside className="hidden lg:flex lg:w-60 lg:flex-col lg:border-r lg:bg-background" />
          <main className="flex-1 overflow-auto p-4 lg:p-6">{children}</main>
        </div>
      </div>
    );
  }

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
