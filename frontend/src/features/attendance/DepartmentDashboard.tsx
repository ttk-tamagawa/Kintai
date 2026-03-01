"use client";

import { useState, useEffect, useCallback } from "react";
import { Loader2, Download, AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { KpiCard } from "@/components/ui/kpi-card";
import { DataTable, type Column } from "@/components/ui/data-table";
import { useAuth } from "@/contexts/AuthContext";
import { useToast } from "@/hooks/useToast";
import {
  fetchDepartmentDashboard,
  exportDepartmentDashboard,
  type DepartmentDashboardParams,
} from "./api";
import { formatHours, getCurrentMonth } from "./utils";
import type {
  DepartmentDashboardKpi,
  DepartmentDashboardItem,
  PageInfo,
} from "@/types";

// ========================================
// 部門別勤怠ダッシュボード（SCR-ATT-004）
// KPIカード4枚（トレンド・アラートバッジ付き）・部門テーブル・CSV出力
// ========================================

export function DepartmentDashboard() {
  const { user, hasAnyRole } = useAuth();
  const toast = useToast();

  // HR/ADMIN は全部署を閲覧可能、MANAGER は自部署のみ
  const isHR = hasAnyRole(["HR", "ADMIN"]);

  // フィルター条件
  const [month, setMonth] = useState(getCurrentMonth());
  const [departmentId, setDepartmentId] = useState(
    isHR ? "" : (user?.departmentId ?? "")
  );

  // KPIデータ（当月+前月）
  const [kpi, setKpi] = useState<DepartmentDashboardKpi | null>(null);
  const [prevKpi, setPrevKpi] = useState<DepartmentDashboardKpi | null>(null);

  // テーブルデータ
  const [data, setData] = useState<DepartmentDashboardItem[]>([]);
  const [pageInfo, setPageInfo] = useState<PageInfo>({
    number: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  const [loading, setLoading] = useState(false);

  // ソート
  const [sortKey, setSortKey] = useState("departmentName");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("asc");

  // CSV出力中フラグ
  const [exporting, setExporting] = useState(false);

  // ========================================
  // データ取得
  // ========================================
  const loadData = useCallback(
    async (page = 0) => {
      setLoading(true);
      try {
        const params: DepartmentDashboardParams = {
          departmentId: departmentId || undefined,
          month,
          page,
          size: 20,
          sort: `${sortKey},${sortDir}`,
        };
        const result = await fetchDepartmentDashboard(params);
        setKpi(result.kpi);
        setPrevKpi(result.previousMonth);
        setData(result.content);
        setPageInfo(result.page);
      } catch (err) {
        toast.apiError(err);
      } finally {
        setLoading(false);
      }
    },
    [departmentId, month, sortKey, sortDir, toast]
  );

  // フィルター変更時にデータを再取得する
  useEffect(() => {
    loadData();
  }, [loadData]);

  // ========================================
  // CSV出力
  // ========================================
  const handleExport = async () => {
    setExporting(true);
    try {
      await exportDepartmentDashboard(departmentId || undefined, month);
      toast.success("CSVファイルをダウンロードしました");
    } catch (err) {
      toast.apiError(err);
    } finally {
      setExporting(false);
    }
  };

  // ========================================
  // KPIトレンド計算（当月 - 前月 の差分）
  // ========================================
  const calcTrend = (
    current: number | undefined,
    previous: number | undefined
  ): number | undefined => {
    if (current == null || previous == null) return undefined;
    return Number((current - previous).toFixed(1));
  };

  // ========================================
  // アラートバッジ（件数が1以上の場合に赤/オレンジで表示）
  // ========================================
  const AlertBadge = ({
    count,
    severity,
  }: {
    count: number;
    severity: "error" | "warning";
  }) => {
    if (count === 0)
      return <span className="text-sm text-muted-foreground">0</span>;
    const colorClass =
      severity === "error"
        ? "bg-red-100 text-red-700 hover:bg-red-100"
        : "bg-orange-100 text-orange-700 hover:bg-orange-100";
    return (
      <Badge variant="secondary" className={`text-xs font-semibold ${colorClass}`}>
        <AlertTriangle className="mr-0.5 h-3 w-3" />
        {count}
      </Badge>
    );
  };

  // ========================================
  // テーブルカラム定義（7カラム）
  // ========================================
  const columns: Column<DepartmentDashboardItem>[] = [
    {
      key: "departmentName",
      label: "部署名",
      sortable: true,
      headerClassName: "w-[140px]",
    },
    {
      key: "headCount",
      label: "人数",
      headerClassName: "w-[80px]",
      cellClassName: "text-right font-mono",
      render: (row) => <span>{row.headCount}</span>,
    },
    {
      key: "avgOvertimeHours",
      label: "平均残業 (h)",
      sortable: true,
      headerClassName: "w-[100px]",
      cellClassName: "text-right font-mono",
      render: (row) => <span>{formatHours(row.avgOvertimeHours)}</span>,
    },
    {
      key: "maxOvertimeHours",
      label: "最大残業 (h)",
      sortable: true,
      headerClassName: "w-[100px] hidden lg:table-cell",
      cellClassName: "text-right font-mono hidden lg:table-cell",
      render: (row) => <span>{formatHours(row.maxOvertimeHours)}</span>,
    },
    {
      key: "overtimeAlertCount",
      label: "36協定超過",
      sortable: true,
      headerClassName: "w-[130px]",
      cellClassName: "text-right",
      render: (row) => (
        <AlertBadge count={row.overtimeAlertCount} severity="error" />
      ),
    },
    {
      key: "missingClockCount",
      label: "未打刻",
      sortable: true,
      headerClassName: "w-[80px]",
      cellClassName: "text-right",
      render: (row) => (
        <AlertBadge count={row.missingClockCount} severity="warning" />
      ),
    },
    {
      key: "attendanceRate",
      label: "出勤率 (%)",
      sortable: true,
      headerClassName: "w-[100px]",
      cellClassName: "text-right font-mono",
      render: (row) => <span>{row.attendanceRate.toFixed(1)}</span>,
    },
  ];

  return (
    <div className="space-y-6">
      {/* フィルタバー: 対象年月 + 部署フィルタ + CSV出力 */}
      <div className="flex flex-wrap items-end justify-between gap-4 rounded-lg bg-gray-50 p-4">
        <div className="flex flex-wrap items-end gap-4">
          <div className="space-y-1">
            <Label className="text-xs">対象年月</Label>
            <Input
              type="month"
              value={month}
              onChange={(e) => setMonth(e.target.value)}
              className="w-[180px]"
            />
          </div>
          {/* HR/ADMIN: 部署IDフィルタで絞り込み可能 */}
          {isHR ? (
            <div className="space-y-1">
              <Label className="text-xs">部署ID</Label>
              <Input
                value={departmentId}
                onChange={(e) => setDepartmentId(e.target.value)}
                placeholder="空欄で全部署"
                className="w-[200px]"
              />
            </div>
          ) : (
            /* MANAGER: 自部署名を表示（変更不可） */
            <div className="space-y-1">
              <Label className="text-xs">部署</Label>
              <p className="flex h-9 items-center text-sm text-muted-foreground">
                {user?.departmentName ?? "---"}
              </p>
            </div>
          )}
        </div>
        <Button variant="outline" onClick={handleExport} disabled={exporting}>
          {exporting ? (
            <Loader2 className="mr-1 h-4 w-4 animate-spin" />
          ) : (
            <Download className="mr-1 h-4 w-4" />
          )}
          {exporting ? "出力中..." : "CSV出力"}
        </Button>
      </div>

      {/* KPIカード（4枚横並び） */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* 全社平均残業時間 */}
        <KpiCard
          label="全社平均残業時間"
          value={kpi ? formatHours(kpi.avgOvertimeHours) : "-"}
          unit="h"
          trend={calcTrend(kpi?.avgOvertimeHours, prevKpi?.avgOvertimeHours)}
        />
        {/* 36協定超過アラート総件数 */}
        <KpiCard
          label="36協定超過アラート"
          value={kpi ? `${kpi.totalOvertimeAlertCount}` : "-"}
          unit="件"
          trend={calcTrend(
            kpi?.totalOvertimeAlertCount,
            prevKpi?.totalOvertimeAlertCount
          )}
          className={
            kpi && kpi.totalOvertimeAlertCount > 0
              ? "border-l-[3px] border-l-red-600"
              : ""
          }
        />
        {/* 未打刻総件数 */}
        <KpiCard
          label="未打刻件数"
          value={kpi ? `${kpi.totalMissingClockCount}` : "-"}
          unit="件"
          trend={calcTrend(
            kpi?.totalMissingClockCount,
            prevKpi?.totalMissingClockCount
          )}
          className={
            kpi && kpi.totalMissingClockCount > 0
              ? "border-l-[3px] border-l-orange-600"
              : ""
          }
        />
        {/* 全社出勤率 */}
        <KpiCard
          label="全社出勤率"
          value={kpi ? kpi.avgAttendanceRate.toFixed(1) : "-"}
          unit="%"
          trend={calcTrend(kpi?.avgAttendanceRate, prevKpi?.avgAttendanceRate)}
        />
      </div>

      {/* 部門別テーブル */}
      <div>
        <h3 className="mb-3 text-sm font-semibold">部門別データ</h3>
        <DataTable<DepartmentDashboardItem>
          columns={columns}
          data={data}
          rowKey={(row) => row.departmentId}
          loading={loading}
          emptyMessage="該当する部門データがありません"
          pagination={{ pageSize: 20 }}
          totalItems={pageInfo.totalElements}
          currentPage={pageInfo.number}
          onPageChange={(page) => loadData(page)}
          onSortChange={(key, dir) => {
            setSortKey(key);
            setSortDir(dir);
          }}
        />
      </div>
    </div>
  );
}
