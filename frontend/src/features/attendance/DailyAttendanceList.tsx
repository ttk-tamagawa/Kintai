"use client";

import { useState, useEffect, useCallback } from "react";
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
import { useAuth } from "@/contexts/AuthContext";
import { useToast } from "@/hooks/useToast";
import { fetchDailyAttendances, type DailyAttendanceParams } from "./api";
import {
  formatTime,
  formatWorkDate,
  formatMinutes,
  getMonthStart,
  getMonthEnd,
} from "./utils";
import { ClockInModal } from "./ClockInModal";
import type { DailyAttendanceItem, PageInfo } from "@/types";

// ========================================
// 日次勤怠一覧（SCR-ATT-002）
// 検索フォーム・DataTable（7カラム）・打刻ボタン連携・ページネーション
// ========================================

/** ステータス選択肢 */
const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: "ALL", label: "全て" },
  { value: "NOT_CLOCKED", label: "未出勤" },
  { value: "CLOCKED_IN", label: "出勤中" },
  { value: "CLOCKED_OUT", label: "退勤済" },
  { value: "FINALIZED", label: "確定済" },
];

export function DailyAttendanceList() {
  const { user, hasAnyRole } = useAuth();
  const toast = useToast();

  // 管理職フラグ（MANAGER/HR/ADMIN は他従業員の検索が可能）
  const isManager = hasAnyRole(["MANAGER", "HR", "ADMIN"]);

  // 検索条件
  const [employeeId, setEmployeeId] = useState(user?.employeeId ?? "");
  const [dateFrom, setDateFrom] = useState(getMonthStart());
  const [dateTo, setDateTo] = useState(getMonthEnd());
  const [status, setStatus] = useState<string>("ALL");

  // 認証復元後にemployeeIdを同期する（useStateの初期値はuser未復元時に空文字になるため）
  useEffect(() => {
    if (user?.employeeId) {
      setEmployeeId(user.employeeId);
    }
  }, [user?.employeeId]);

  // テーブルデータ
  const [data, setData] = useState<DailyAttendanceItem[]>([]);
  const [pageInfo, setPageInfo] = useState<PageInfo>({
    number: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  const [loading, setLoading] = useState(false);

  // ソート
  const [sortKey, setSortKey] = useState("workDate");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");

  // 打刻モーダル
  const [clockModalOpen, setClockModalOpen] = useState(false);

  // ========================================
  // データ取得
  // ========================================
  const loadData = useCallback(
    async (page = 0) => {
      // employeeIdが未設定の場合はAPI呼び出しをスキップする（未ログイン時）
      if (!employeeId) return;
      setLoading(true);
      try {
        const params: DailyAttendanceParams = {
          employeeId,
          dateFrom,
          dateTo,
          status: status === "ALL" ? undefined : status,
          page,
          size: 20,
          sortField: sortKey,
          sortDirection: sortDir,
        };
        const result = await fetchDailyAttendances(params);
        setData(result.content);
        setPageInfo(result.page);
      } catch (err) {
        toast.apiError(err);
      } finally {
        setLoading(false);
      }
    },
    [employeeId, dateFrom, dateTo, status, sortKey, sortDir, toast]
  );

  // 初回読み込み
  useEffect(() => {
    loadData();
  }, [loadData]);

  // ========================================
  // 検索・クリア・ページ変更ハンドラー
  // ========================================
  const handleSearch = () => loadData(0);

  const handleClear = () => {
    setEmployeeId(user?.employeeId ?? "");
    setDateFrom(getMonthStart());
    setDateTo(getMonthEnd());
    setStatus("ALL");
  };

  const handlePageChange = (page: number) => loadData(page);

  const handleSortChange = (key: string, direction: "asc" | "desc") => {
    setSortKey(key);
    setSortDir(direction);
  };

  // 打刻後にリストを再取得する
  const handleClockAction = () => loadData(pageInfo.number);

  // ========================================
  // テーブルカラム定義（7カラム: 勤務日・出勤・退勤・休憩・実労働・残業・ステータス）
  // ========================================
  const columns: Column<DailyAttendanceItem>[] = [
    {
      key: "workDate",
      label: "勤務日",
      sortable: true,
      headerClassName: "w-[120px]",
      render: (row) => (
        <span className="font-mono text-sm">
          {formatWorkDate(row.workDate)}
        </span>
      ),
    },
    {
      key: "clockInTime",
      label: "出勤",
      headerClassName: "w-[80px]",
      render: (row) => (
        <span className="font-mono text-sm">{formatTime(row.clockInTime)}</span>
      ),
    },
    {
      key: "clockOutTime",
      label: "退勤",
      headerClassName: "w-[80px]",
      render: (row) => (
        <span className="font-mono text-sm">{formatTime(row.clockOutTime)}</span>
      ),
    },
    {
      key: "breakMinutes",
      label: "休憩",
      headerClassName: "w-[80px] hidden md:table-cell",
      cellClassName: "hidden md:table-cell",
      render: (row) => (
        <span className="font-mono text-sm">
          {formatMinutes(row.breakMinutes)}
        </span>
      ),
    },
    {
      key: "netWorkMinutes",
      label: "実労働",
      headerClassName: "w-[100px]",
      render: (row) => (
        <span className="font-mono text-sm">
          {formatMinutes(row.netWorkMinutes)}
        </span>
      ),
    },
    {
      key: "totalOvertimeMinutes",
      label: "残業",
      sortable: true,
      headerClassName: "w-[80px] hidden md:table-cell",
      cellClassName: "hidden md:table-cell",
      render: (row) => (
        <span className="font-mono text-sm">
          {formatMinutes(row.totalOvertimeMinutes)}
        </span>
      ),
    },
    {
      key: "status",
      label: "ステータス",
      sortable: true,
      headerClassName: "w-[100px]",
      render: (row) => <StatusBadge status={row.status} />,
    },
  ];

  return (
    <div className="space-y-4">
      {/* 検索フォーム */}
      <div className="rounded-lg bg-gray-50 p-4">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {/* 従業員ID（管理職のみ表示: 他従業員の勤怠を検索可能） */}
          {isManager && (
            <div className="space-y-1">
              <Label className="text-xs">従業員ID</Label>
              <Input
                value={employeeId}
                onChange={(e) => setEmployeeId(e.target.value)}
                placeholder="従業員IDを入力"
              />
            </div>
          )}
          {/* 勤務期間: 開始日 */}
          <div className="space-y-1">
            <Label className="text-xs">勤務期間（開始）</Label>
            <Input
              type="date"
              value={dateFrom}
              onChange={(e) => setDateFrom(e.target.value)}
            />
          </div>
          {/* 勤務期間: 終了日 */}
          <div className="space-y-1">
            <Label className="text-xs">勤務期間（終了）</Label>
            <Input
              type="date"
              value={dateTo}
              onChange={(e) => setDateTo(e.target.value)}
            />
          </div>
          {/* ステータスフィルタ */}
          <div className="space-y-1">
            <Label className="text-xs">ステータス</Label>
            <Select value={status} onValueChange={setStatus}>
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

      {/* アクションバー: 件数表示 + 打刻ボタン */}
      <div className="flex items-center justify-between">
        <span className="text-sm text-muted-foreground">
          {pageInfo.totalElements} 件
        </span>
        <Button onClick={() => setClockModalOpen(true)}>打刻する</Button>
      </div>

      {/* データテーブル */}
      <DataTable<DailyAttendanceItem>
        columns={columns}
        data={data}
        rowKey={(row) => row.attendanceId ?? row.workDate}
        loading={loading}
        emptyMessage="該当する勤怠データがありません"
        pagination={{ pageSize: 20 }}
        totalItems={pageInfo.totalElements}
        currentPage={pageInfo.number}
        onPageChange={handlePageChange}
        onSortChange={handleSortChange}
      />

      {/* 打刻モーダル */}
      <ClockInModal
        open={clockModalOpen}
        onClose={() => setClockModalOpen(false)}
        onClockAction={handleClockAction}
      />
    </div>
  );
}
