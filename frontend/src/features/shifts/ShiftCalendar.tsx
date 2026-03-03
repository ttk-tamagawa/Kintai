"use client";

import { useState, useEffect, useCallback } from "react";
import { format, parseISO } from "date-fns";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { DataTable, type Column } from "@/components/ui/data-table";
import { StatusBadge } from "@/components/ui/status-badge";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useAuth } from "@/contexts/AuthContext";
import { useToast } from "@/hooks/useToast";
import {
  fetchSchedules,
  publishSchedule,
  type ScheduleListParams,
} from "./api";
import { ShiftAssignModal } from "./ShiftAssignModal";
import type { ScheduleItem, DayOfWeek } from "@/types";

// ========================================
// シフトカレンダー（SCR-SHF-003）
// 検索フォーム・12カラムテーブル・一括公開・割当モーダル連携
// バックエンドはページネーション非対応のため全件取得
// ========================================

/** 曜日キーとラベルの対応 */
const DAYS: { key: DayOfWeek; label: string }[] = [
  { key: "MONDAY", label: "月" },
  { key: "TUESDAY", label: "火" },
  { key: "WEDNESDAY", label: "水" },
  { key: "THURSDAY", label: "木" },
  { key: "FRIDAY", label: "金" },
  { key: "SATURDAY", label: "土" },
  { key: "SUNDAY", label: "日" },
];

/** ステータスフィルタの選択肢 */
const STATUS_OPTIONS = [
  { value: "ALL", label: "全て" },
  { value: "DRAFT", label: "下書き" },
  { value: "PUBLISHED", label: "公開済" },
];

/** 今週の月曜日を YYYY-MM-DD で取得する */
function getThisMonday(): string {
  const now = new Date();
  const day = now.getDay();
  // 日曜日(0)なら -6日、それ以外は 1-day 日前
  const diff = day === 0 ? -6 : 1 - day;
  const monday = new Date(now);
  monday.setDate(now.getDate() + diff);
  return format(monday, "yyyy-MM-dd");
}

/** 指定日付から n週間後の日付を YYYY-MM-DD で取得する */
function addWeeks(dateStr: string, weeks: number): string {
  const date = parseISO(dateStr);
  date.setDate(date.getDate() + weeks * 7);
  return format(date, "yyyy-MM-dd");
}

/** 週開始日を MM/dd 形式でフォーマットする */
function formatWeekStart(dateStr: string): string {
  try {
    return format(parseISO(dateStr), "MM/dd");
  } catch {
    return dateStr;
  }
}

export function ShiftCalendar() {
  const { hasAnyRole } = useAuth();
  const toast = useToast();
  const isManager = hasAnyRole(["MANAGER", "HR", "ADMIN"]);

  // 検索条件
  const [weekFrom, setWeekFrom] = useState(() => getThisMonday());
  const [weekTo, setWeekTo] = useState(() => addWeeks(getThisMonday(), 3));
  const [statusFilter, setStatusFilter] = useState("ALL");

  // テーブルデータ（バックエンドはページネーション非対応のため全件取得）
  const [data, setData] = useState<ScheduleItem[]>([]);
  const [loading, setLoading] = useState(false);

  // 行選択（DRAFTスケジュールのみ選択可）
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  // 割当モーダル
  const [assignModalOpen, setAssignModalOpen] = useState(false);
  const [assignMode, setAssignMode] = useState<"create" | "edit">("create");
  const [editTarget, setEditTarget] = useState<ScheduleItem | null>(null);

  // 一括公開確認ダイアログ
  const [bulkPublishOpen, setBulkPublishOpen] = useState(false);
  const [bulkPublishing, setBulkPublishing] = useState(false);

  // ========================================
  // データ取得
  // ========================================
  const loadData = useCallback(
    async () => {
      setLoading(true);
      try {
        const params: ScheduleListParams = {
          weekFrom,
          weekTo,
        };
        // バックエンドは配列を直接返す（ページネーション非対応）
        let result = await fetchSchedules(params);
        // フロントエンド側でステータスフィルタを適用する
        if (statusFilter !== "ALL") {
          result = result.filter((s) => s.status === statusFilter);
        }
        setData(result);
        setSelectedIds([]);
      } catch (err) {
        toast.apiError(err);
      } finally {
        setLoading(false);
      }
    },
    [weekFrom, weekTo, statusFilter, toast]
  );

  // 初回読み込み
  useEffect(() => {
    loadData();
  }, [loadData]);

  // ========================================
  // ハンドラー
  // ========================================
  const handleSearch = () => loadData();

  const handleClear = () => {
    const monday = getThisMonday();
    setWeekFrom(monday);
    setWeekTo(addWeeks(monday, 3));
    setStatusFilter("ALL");
  };

  // 行選択トグル（DRAFTスケジュールのみ許可）
  const handleToggleSelect = (scheduleId: string) => {
    setSelectedIds((prev) =>
      prev.includes(scheduleId)
        ? prev.filter((id) => id !== scheduleId)
        : [...prev, scheduleId]
    );
  };

  // 全DRAFT選択/全解除
  const draftIds = data
    .filter((s) => s.status === "DRAFT")
    .map((s) => s.scheduleId);
  const allDraftSelected =
    draftIds.length > 0 && draftIds.every((id) => selectedIds.includes(id));

  const handleToggleSelectAll = () => {
    if (allDraftSelected) {
      setSelectedIds([]);
    } else {
      setSelectedIds(draftIds);
    }
  };

  // 行クリック → 編集モーダル（管理職のみ）
  const handleRowClick = (row: ScheduleItem) => {
    if (!isManager) return;
    setEditTarget(row);
    setAssignMode("edit");
    setAssignModalOpen(true);
  };

  // 新規割当モーダルを開く
  const openCreateModal = () => {
    setEditTarget(null);
    setAssignMode("create");
    setAssignModalOpen(true);
  };

  // 割当/変更完了後にリストを再取得する
  const handleAssignComplete = () => {
    setAssignModalOpen(false);
    setEditTarget(null);
    loadData();
  };

  // ========================================
  // 一括公開の実行
  // ========================================
  const handleBulkPublish = async () => {
    setBulkPublishing(true);
    let successCount = 0;
    let failCount = 0;

    // 選択されたスケジュールを順次公開する
    for (const id of selectedIds) {
      try {
        await publishSchedule(id);
        successCount++;
      } catch {
        failCount++;
      }
    }

    setBulkPublishing(false);
    setBulkPublishOpen(false);
    setSelectedIds([]);

    // 結果をToastで通知する
    if (failCount === 0) {
      toast.success(`${successCount} 件のスケジュールを公開しました`);
    } else {
      toast.warning(
        `${successCount}/${selectedIds.length} 件を公開しました。${failCount} 件は公開できませんでした。`
      );
    }
    loadData();
  };

  // ========================================
  // テーブルカラム定義（12カラム: チェックボックス + 従業員名 + 週開始 + 月〜日 + ステータス）
  // ========================================
  const columns: Column<ScheduleItem>[] = [
    // チェックボックス列（DRAFT のみ選択可能）
    {
      key: "_select",
      label: "",
      headerClassName: "w-[40px]",
      render: (row) => (
        <input
          type="checkbox"
          disabled={row.status === "PUBLISHED"}
          checked={selectedIds.includes(row.scheduleId)}
          onChange={() => handleToggleSelect(row.scheduleId)}
          onClick={(e) => e.stopPropagation()}
          className="h-4 w-4 disabled:opacity-30"
        />
      ),
    },
    {
      key: "employeeName",
      label: "従業員",
      headerClassName: "w-[120px]",
      // employeeName がバックエンドから返らないため employeeId を表示する
      render: (row) => (
        <span className="text-sm">{row.employeeName || row.employeeId}</span>
      ),
    },
    {
      key: "weekStartDate",
      label: "週開始",
      headerClassName: "w-[80px]",
      render: (row) => (
        <span className="font-mono text-sm">
          {formatWeekStart(row.weekStartDate)}
        </span>
      ),
    },
    // 月〜日の7カラム（土日はlg以下で非表示）
    ...DAYS.map(({ key, label }) => ({
      key,
      label,
      headerClassName:
        "w-[60px] text-center" +
        (key === "SATURDAY" || key === "SUNDAY"
          ? " hidden lg:table-cell"
          : ""),
      cellClassName:
        "text-center" +
        (key === "SATURDAY" || key === "SUNDAY"
          ? " hidden lg:table-cell"
          : ""),
      render: (row: ScheduleItem) => {
        const assignment = row.assignments[key];
        if (!assignment) {
          return <span className="text-gray-300">-</span>;
        }
        return <span className="text-xs">{assignment.patternName}</span>;
      },
    })),
    {
      key: "status",
      label: "ステータス",
      headerClassName: "w-[80px]",
      render: (row) => <StatusBadge status={row.status} />,
    },
  ];

  return (
    <div className="space-y-4">
      {/* 検索フォーム */}
      <div className="rounded-lg bg-gray-50 p-4">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {/* 期間: 開始 */}
          <div className="space-y-1">
            <Label className="text-xs">期間（開始）</Label>
            <Input
              type="date"
              value={weekFrom}
              onChange={(e) => setWeekFrom(e.target.value)}
            />
          </div>
          {/* 期間: 終了 */}
          <div className="space-y-1">
            <Label className="text-xs">期間（終了）</Label>
            <Input
              type="date"
              value={weekTo}
              onChange={(e) => setWeekTo(e.target.value)}
            />
          </div>
          {/* ステータスフィルタ */}
          <div className="space-y-1">
            <Label className="text-xs">ステータス</Label>
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {STATUS_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {/* 検索・クリアボタン */}
          <div className="flex items-end gap-2">
            <Button variant="outline" onClick={handleClear}>
              クリア
            </Button>
            <Button onClick={handleSearch}>検索</Button>
          </div>
        </div>
      </div>

      {/* アクションバー: 件数 + 全選択 + 一括公開 + シフト割当ボタン */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <span className="text-sm text-muted-foreground">
            {data.length} 件
          </span>
          {/* DRAFT行の全選択チェックボックス */}
          {draftIds.length > 0 && (
            <label className="flex items-center gap-1.5 text-sm text-muted-foreground">
              <input
                type="checkbox"
                checked={allDraftSelected}
                onChange={handleToggleSelectAll}
                className="h-4 w-4"
              />
              全下書き選択
            </label>
          )}
        </div>
        <div className="flex items-center gap-2">
          {isManager && (
            <>
              <Button
                variant="outline"
                disabled={selectedIds.length === 0}
                onClick={() => setBulkPublishOpen(true)}
              >
                一括公開
              </Button>
              <Button onClick={openCreateModal}>+ シフト割当</Button>
            </>
          )}
        </div>
      </div>

      {/* データテーブル */}
      <DataTable<ScheduleItem>
        columns={columns}
        data={data}
        rowKey={(row) => row.scheduleId}
        loading={loading}
        emptyMessage="シフトスケジュールがありません"
        pagination={{ pageSize: 20 }}
        onRowClick={isManager ? handleRowClick : undefined}
      />

      {/* シフト割当モーダル */}
      <ShiftAssignModal
        open={assignModalOpen}
        mode={assignMode}
        editTarget={editTarget}
        onClose={() => {
          setAssignModalOpen(false);
          setEditTarget(null);
        }}
        onComplete={handleAssignComplete}
      />

      {/* 一括公開確認ダイアログ */}
      <ConfirmDialog
        open={bulkPublishOpen}
        onClose={() => setBulkPublishOpen(false)}
        onConfirm={handleBulkPublish}
        title="一括公開"
        description={`選択した ${selectedIds.length} 件のスケジュールを公開しますか？公開すると対象従業員に通知されます。`}
        confirmLabel="公開する"
        loading={bulkPublishing}
      />
    </div>
  );
}
