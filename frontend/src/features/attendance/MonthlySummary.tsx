"use client";

import { useState, useEffect, useCallback } from "react";
import { Loader2, Download } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { KpiCard } from "@/components/ui/kpi-card";
import { DataTable, type Column } from "@/components/ui/data-table";
import { useAuth } from "@/contexts/AuthContext";
import { useToast } from "@/hooks/useToast";
import {
  fetchMonthlySummary,
  exportMonthlySummary,
  type MonthlySummaryParams,
} from "./api";
import { formatHours, getCurrentMonth } from "./utils";
import type {
  MonthlySummaryKpi,
  MonthlyEmployeeSummary,
  PageInfo,
} from "@/types";

// ========================================
// 月次勤怠サマリー（SCR-ATT-003）
// フィルタバー・KPIカード4枚・従業員サマリーテーブル・CSV出力
// ========================================

export function MonthlySummary() {
  const { user } = useAuth();
  const toast = useToast();

  // フィルター条件
  const [month, setMonth] = useState(getCurrentMonth());
  const [departmentId] = useState(user?.departmentId ?? "");

  // KPIデータ
  const [kpi, setKpi] = useState<MonthlySummaryKpi | null>(null);

  // テーブルデータ
  const [data, setData] = useState<MonthlyEmployeeSummary[]>([]);
  const [pageInfo, setPageInfo] = useState<PageInfo>({
    number: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  const [loading, setLoading] = useState(false);

  // ソート
  const [sortKey, setSortKey] = useState("employeeName");
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
        const params: MonthlySummaryParams = {
          departmentId: departmentId || undefined,
          month,
          page,
          size: 20,
          sort: `${sortKey},${sortDir}`,
        };
        const result = await fetchMonthlySummary(params);
        setKpi(result.kpi);
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
      await exportMonthlySummary(departmentId || undefined, month);
      toast.success("CSVファイルをダウンロードしました");
    } catch (err) {
      toast.apiError(err);
    } finally {
      setExporting(false);
    }
  };

  // ========================================
  // テーブルカラム定義（7カラム）
  // ========================================
  const columns: Column<MonthlyEmployeeSummary>[] = [
    {
      key: "employeeName",
      label: "従業員名",
      sortable: true,
      headerClassName: "w-[140px]",
    },
    {
      key: "workDays",
      label: "出勤日数",
      headerClassName: "w-[80px]",
      cellClassName: "text-right font-mono",
      render: (row) => <span>{row.workDays}</span>,
    },
    {
      key: "totalWorkHours",
      label: "総労働時間",
      sortable: true,
      headerClassName: "w-[120px]",
      cellClassName: "text-right font-mono",
      render: (row) => <span>{formatHours(row.totalWorkHours)}h</span>,
    },
    {
      key: "totalOvertimeHours",
      label: "総残業時間",
      sortable: true,
      headerClassName: "w-[120px]",
      cellClassName: "text-right font-mono",
      render: (row) => <span>{formatHours(row.totalOvertimeHours)}h</span>,
    },
    {
      key: "lateNightHours",
      label: "深夜勤務",
      headerClassName: "w-[120px] hidden lg:table-cell",
      cellClassName: "text-right font-mono hidden lg:table-cell",
      render: (row) => <span>{formatHours(row.lateNightHours)}h</span>,
    },
    {
      key: "paidLeaveUsed",
      label: "有給使用",
      headerClassName: "w-[100px]",
      cellClassName: "text-right font-mono",
      render: (row) => <span>{row.paidLeaveUsed.toFixed(1)}</span>,
    },
    {
      key: "employeeId",
      label: "従業員ID",
      headerClassName: "w-[80px] hidden lg:table-cell",
      cellClassName: "hidden lg:table-cell text-muted-foreground text-xs",
    },
  ];

  return (
    <div className="space-y-6">
      {/* フィルタバー: 対象年月 + CSV出力 */}
      <div className="flex flex-wrap items-end justify-between gap-4 rounded-lg bg-gray-50 p-4">
        <div className="space-y-1">
          <Label className="text-xs">対象年月</Label>
          <Input
            type="month"
            value={month}
            onChange={(e) => setMonth(e.target.value)}
            className="w-[180px]"
          />
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
        <KpiCard
          label="出勤日数"
          value={kpi ? `${kpi.totalWorkDays}` : "-"}
          unit="日"
          subText={kpi ? `平均: ${kpi.avgWorkDays.toFixed(1)}日` : ""}
        />
        <KpiCard
          label="総労働時間"
          value={kpi ? formatHours(kpi.totalWorkHours) : "-"}
          unit="h"
          subText={kpi ? `平均: ${formatHours(kpi.avgWorkHours)}h` : ""}
        />
        <KpiCard
          label="総残業時間"
          value={kpi ? formatHours(kpi.totalOvertimeHours) : "-"}
          unit="h"
          subText={
            kpi ? `平均: ${formatHours(kpi.avgOvertimeHours)}h` : ""
          }
        />
        <KpiCard
          label="有給使用日数"
          value={kpi ? `${kpi.totalPaidLeaveUsed}` : "-"}
          unit="日"
        />
      </div>

      {/* 従業員別サマリーテーブル */}
      <div>
        <h3 className="mb-3 text-sm font-semibold">従業員別サマリー</h3>
        <DataTable<MonthlyEmployeeSummary>
          columns={columns}
          data={data}
          rowKey={(row) => row.employeeId}
          loading={loading}
          emptyMessage="該当する勤怠サマリーがありません"
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
