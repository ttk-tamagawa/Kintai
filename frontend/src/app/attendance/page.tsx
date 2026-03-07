"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/contexts/AuthContext";
import {
  DailyAttendanceList,
  MonthlySummary,
  DepartmentDashboard,
} from "@/features/attendance";

// ========================================
// 勤怠管理ページ
// タブナビゲーション: 日次一覧 / 月次サマリー / 部門ダッシュボード
// ========================================

/** タブ定義 */
type TabId = "daily" | "monthly" | "department";

interface TabDef {
  id: TabId;
  label: string;
  /** このタブを表示可能なロール（undefinedなら全員表示） */
  roles?: string[];
}

const TABS: TabDef[] = [
  { id: "daily", label: "日次勤怠一覧" },
  { id: "monthly", label: "月次サマリー", roles: ["MANAGER", "HR"] },
  {
    id: "department",
    label: "部門ダッシュボード",
    roles: ["HR", "ADMIN"],
  },
];

export default function AttendancePage() {
  const { hasAnyRole } = useAuth();
  const [activeTab, setActiveTab] = useState<TabId>("daily");

  // ロールに基づいて表示可能なタブをフィルタする
  const visibleTabs = TABS.filter(
    (tab) =>
      !tab.roles || hasAnyRole(tab.roles as ("EMPLOYEE" | "MANAGER" | "HR" | "ADMIN")[])
  );

  return (
    <div className="space-y-6">
      {/* ページヘッダー */}
      <div>
        <h2 className="text-2xl font-bold tracking-tight">勤怠管理</h2>
        <p className="text-sm text-muted-foreground">
          勤怠打刻・日次一覧・月次サマリーを管理します
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
      {activeTab === "daily" && <DailyAttendanceList />}
      {activeTab === "monthly" && <MonthlySummary />}
      {activeTab === "department" && <DepartmentDashboard />}
    </div>
  );
}
