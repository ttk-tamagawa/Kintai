"use client";

import { useState, useEffect, useCallback } from "react";
import { MoreHorizontal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { DataTable, type Column } from "@/components/ui/data-table";
import { StatusBadge } from "@/components/ui/status-badge";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useToast } from "@/hooks/useToast";
import {
  fetchPatterns,
  deactivatePattern,
  reactivatePattern,
  type PatternListParams,
} from "./api";
import { ShiftPatternModal } from "./ShiftPatternModal";
import type { ShiftPattern, PageInfo } from "@/types";

// ========================================
// シフトパターン一覧（SCR-SHF-001）
// 有効/無効フィルタ・7カラムテーブル・無効化/再有効化アクション
// ========================================

/** 有効/無効フィルタの選択肢 */
const ACTIVE_OPTIONS = [
  { value: "ALL", label: "全て" },
  { value: "true", label: "有効のみ" },
  { value: "false", label: "無効のみ" },
];

export function ShiftPatternList() {
  const toast = useToast();

  // 検索条件
  const [activeFilter, setActiveFilter] = useState("ALL");

  // テーブルデータ
  const [data, setData] = useState<ShiftPattern[]>([]);
  const [pageInfo, setPageInfo] = useState<PageInfo>({
    number: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  const [loading, setLoading] = useState(false);

  // ソート（デフォルト: パターン名昇順）
  const [sortKey, setSortKey] = useState("name");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("asc");

  // 登録モーダル表示状態
  const [createModalOpen, setCreateModalOpen] = useState(false);

  // 確認ダイアログ
  const [confirmTarget, setConfirmTarget] = useState<ShiftPattern | null>(null);
  const [confirmAction, setConfirmAction] = useState<
    "deactivate" | "reactivate"
  >("deactivate");
  const [confirmLoading, setConfirmLoading] = useState(false);

  // ========================================
  // データ取得
  // ========================================
  const loadData = useCallback(
    async (page = 0) => {
      setLoading(true);
      try {
        const params: PatternListParams = {
          isActive:
            activeFilter === "ALL" ? undefined : activeFilter === "true",
          page,
          size: 20,
          sort: `${sortKey},${sortDir}`,
        };
        const result = await fetchPatterns(params);
        setData(result.content);
        setPageInfo(result.page);
      } catch (err) {
        toast.apiError(err);
      } finally {
        setLoading(false);
      }
    },
    [activeFilter, sortKey, sortDir, toast]
  );

  // 初回読み込み
  useEffect(() => {
    loadData();
  }, [loadData]);

  // ========================================
  // ハンドラー
  // ========================================
  const handleSearch = () => loadData(0);
  const handleClear = () => setActiveFilter("ALL");
  const handlePageChange = (page: number) => loadData(page);

  const handleSortChange = (key: string, direction: "asc" | "desc") => {
    setSortKey(key);
    setSortDir(direction);
  };

  // 確認ダイアログを開く
  const openConfirm = (
    pattern: ShiftPattern,
    action: "deactivate" | "reactivate"
  ) => {
    setConfirmTarget(pattern);
    setConfirmAction(action);
  };

  // 無効化/再有効化の実行
  const handleConfirm = async () => {
    if (!confirmTarget) return;
    setConfirmLoading(true);
    try {
      if (confirmAction === "deactivate") {
        await deactivatePattern(confirmTarget.patternId);
        toast.success(`「${confirmTarget.name}」を無効化しました`);
      } else {
        await reactivatePattern(confirmTarget.patternId);
        toast.success(`「${confirmTarget.name}」を再有効化しました`);
      }
      setConfirmTarget(null);
      loadData(pageInfo.number);
    } catch (err) {
      toast.apiError(err);
    } finally {
      setConfirmLoading(false);
    }
  };

  // 登録完了後にリストを再取得する
  const handleCreated = () => {
    setCreateModalOpen(false);
    loadData(0);
  };

  // ========================================
  // テーブルカラム定義（7カラム）
  // ========================================
  const columns: Column<ShiftPattern>[] = [
    {
      key: "name",
      label: "パターン名",
      sortable: true,
      headerClassName: "w-[150px]",
    },
    {
      key: "startTime",
      label: "開始時刻",
      headerClassName: "w-[100px]",
      render: (row) => (
        <span className="font-mono text-sm">{row.startTime}</span>
      ),
    },
    {
      key: "endTime",
      label: "終了時刻",
      headerClassName: "w-[100px]",
      render: (row) => (
        <span className="font-mono text-sm">{row.endTime}</span>
      ),
    },
    {
      key: "breakMinutes",
      label: "休憩",
      headerClassName: "w-[80px] hidden md:table-cell",
      cellClassName: "hidden md:table-cell",
      render: (row) => <span className="text-sm">{row.breakMinutes}分</span>,
    },
    {
      key: "isOvernight",
      label: "夜勤",
      headerClassName: "w-[60px] hidden md:table-cell",
      cellClassName: "hidden md:table-cell",
      render: (row) =>
        row.isOvernight ? (
          <Badge
            variant="secondary"
            className="bg-indigo-100 text-xs text-indigo-700 hover:bg-indigo-100"
          >
            夜勤
          </Badge>
        ) : (
          <span className="text-muted-foreground">-</span>
        ),
    },
    {
      key: "isActive",
      label: "有効",
      headerClassName: "w-[60px]",
      render: (row) => (
        <StatusBadge status={row.isActive ? "ACTIVE" : "INACTIVE"} />
      ),
    },
    {
      key: "actions",
      label: "操作",
      headerClassName: "w-[60px]",
      render: (row) => (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8"
              onClick={(e) => e.stopPropagation()}
            >
              <MoreHorizontal className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {row.isActive ? (
              <DropdownMenuItem
                onClick={() => openConfirm(row, "deactivate")}
              >
                無効化
              </DropdownMenuItem>
            ) : (
              <DropdownMenuItem
                onClick={() => openConfirm(row, "reactivate")}
              >
                再有効化
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      {/* 検索フォーム */}
      <div className="rounded-lg bg-gray-50 p-4">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {/* 有効/無効フィルタ */}
          <div className="space-y-1">
            <Label className="text-xs">有効/無効</Label>
            <Select value={activeFilter} onValueChange={setActiveFilter}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ACTIVE_OPTIONS.map((opt) => (
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

      {/* アクションバー: 件数 + 新規登録ボタン */}
      <div className="flex items-center justify-between">
        <span className="text-sm text-muted-foreground">
          {pageInfo.totalElements} 件
        </span>
        <Button onClick={() => setCreateModalOpen(true)}>+ 新規登録</Button>
      </div>

      {/* データテーブル */}
      <DataTable<ShiftPattern>
        columns={columns}
        data={data}
        rowKey={(row) => row.patternId}
        loading={loading}
        emptyMessage="シフトパターンが登録されていません"
        pagination={{ pageSize: 20 }}
        totalItems={pageInfo.totalElements}
        currentPage={pageInfo.number}
        onPageChange={handlePageChange}
        onSortChange={handleSortChange}
      />

      {/* パターン登録モーダル */}
      <ShiftPatternModal
        open={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        onCreated={handleCreated}
      />

      {/* 無効化/再有効化の確認ダイアログ */}
      {confirmTarget && (
        <ConfirmDialog
          open={!!confirmTarget}
          onClose={() => setConfirmTarget(null)}
          onConfirm={handleConfirm}
          title={
            confirmAction === "deactivate"
              ? "パターン無効化"
              : "パターン再有効化"
          }
          description={
            confirmAction === "deactivate"
              ? `「${confirmTarget.name}」を無効化しますか？未来の割当で使用されている場合は無効化できません。`
              : `「${confirmTarget.name}」を再有効化しますか？`
          }
          confirmLabel={
            confirmAction === "deactivate" ? "無効化する" : "再有効化する"
          }
          destructive={confirmAction === "deactivate"}
          loading={confirmLoading}
        />
      )}
    </div>
  );
}
