"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/contexts/AuthContext";
import { ShiftPatternList, ShiftCalendar } from "@/features/shifts";

// ========================================
// シフト管理ページ
// タブナビゲーション: パターン管理 / シフトカレンダー
// ========================================

/** タブ定義 */
type TabId = "patterns" | "calendar";

interface TabDef {
  id: TabId;
  label: string;
  /** このタブを表示可能なロール（undefinedなら全員表示） */
  roles?: string[];
}

const TABS: TabDef[] = [
  { id: "patterns", label: "パターン管理", roles: ["MANAGER", "HR", "ADMIN"] },
  { id: "calendar", label: "シフトカレンダー" },
];

export default function ShiftsPage() {
  const { hasAnyRole } = useAuth();

  // ロールに基づいて表示可能なタブをフィルタする
  const visibleTabs = TABS.filter(
    (tab) =>
      !tab.roles ||
      hasAnyRole(tab.roles as ("EMPLOYEE" | "MANAGER" | "HR" | "ADMIN")[])
  );

  // 最初の表示可能タブをデフォルトにする
  const [activeTab, setActiveTab] = useState<TabId>(
    visibleTabs[0]?.id ?? "calendar"
  );

  return (
    <div className="space-y-6">
      {/* ページヘッダー */}
      <div>
        <h2 className="text-2xl font-bold tracking-tight">シフト管理</h2>
        <p className="text-sm text-muted-foreground">
          シフトパターン定義・週次スケジュール管理を行います
        </p>
      </div>

      {/* タブナビゲーション */}
      <div className="border-b">
        <nav className="-mb-px flex gap-4">
          {visibleTabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={cn(
                "whitespace-nowrap border-b-2 px-1 py-2 text-sm font-medium transition-colors",
                activeTab === tab.id
                  ? "border-primary text-foreground"
                  : "border-transparent text-muted-foreground hover:border-muted-foreground/30 hover:text-foreground"
              )}
            >
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      {/* タブコンテンツ */}
      {activeTab === "patterns" && <ShiftPatternList />}
      {activeTab === "calendar" && <ShiftCalendar />}
    </div>
  );
}
