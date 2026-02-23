"use client";

import { useState, useMemo, type ReactNode } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ArrowUpDown, ArrowUp, ArrowDown, ChevronLeft, ChevronRight } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";

// ========================================
// DataTable コンポーネント
// ソート・ページネーション・行選択対応の汎用テーブル
// ========================================

/** カラム定義 */
export interface Column<T> {
  /** カラムのキー */
  key: string;
  /** ヘッダーラベル */
  label: string;
  /** ソート可能かどうか（デフォルト: false） */
  sortable?: boolean;
  /** カスタムレンダラー */
  render?: (row: T) => ReactNode;
  /** テーブルヘッダーのクラス名 */
  headerClassName?: string;
  /** テーブルセルのクラス名 */
  cellClassName?: string;
}

/** ソート方向 */
type SortDirection = "asc" | "desc";

/** ソート状態 */
interface SortState {
  key: string;
  direction: SortDirection;
}

/** ページネーション設定 */
interface PaginationConfig {
  /** 1ページあたりの表示件数（デフォルト: 20） */
  pageSize?: number;
  /** 表示件数の選択肢 */
  pageSizeOptions?: number[];
}

interface DataTableProps<T> {
  /** カラム定義 */
  columns: Column<T>[];
  /** テーブルデータ */
  data: T[];
  /** 各行のユニークキーを返す関数 */
  rowKey: (row: T) => string;
  /** ページネーション設定 */
  pagination?: PaginationConfig;
  /** サーバーサイドページネーション用: 全件数 */
  totalItems?: number;
  /** サーバーサイドページネーション用: 現在ページ（0始まり） */
  currentPage?: number;
  /** サーバーサイドページネーション用: ページ変更ハンドラー */
  onPageChange?: (page: number) => void;
  /** サーバーサイドソート用: ソート変更ハンドラー */
  onSortChange?: (key: string, direction: SortDirection) => void;
  /** 行クリック時のハンドラー */
  onRowClick?: (row: T) => void;
  /** 行選択有効化 */
  selectable?: boolean;
  /** 選択された行のキー一覧 */
  selectedKeys?: string[];
  /** 行選択変更ハンドラー */
  onSelectionChange?: (keys: string[]) => void;
  /** ローディング中かどうか */
  loading?: boolean;
  /** データなしメッセージ */
  emptyMessage?: string;
}

export function DataTable<T>({
  columns,
  data,
  rowKey,
  pagination,
  totalItems,
  currentPage = 0,
  onPageChange,
  onSortChange,
  onRowClick,
  selectable = false,
  selectedKeys = [],
  onSelectionChange,
  loading = false,
  emptyMessage = "データがありません",
}: DataTableProps<T>) {
  const [sort, setSort] = useState<SortState | null>(null);
  const [pageSize, setPageSize] = useState(pagination?.pageSize ?? 20);

  const pageSizeOptions = pagination?.pageSizeOptions ?? [10, 20, 50];

  // ソート切替ハンドラー
  const handleSort = (key: string) => {
    let newDirection: SortDirection = "asc";
    if (sort?.key === key && sort.direction === "asc") {
      newDirection = "desc";
    }
    const newSort = { key, direction: newDirection };
    setSort(newSort);
    onSortChange?.(key, newDirection);
  };

  // クライアントサイドソート（サーバーサイドでない場合）
  const sortedData = useMemo(() => {
    if (!sort || onSortChange) return data;
    return [...data].sort((a, b) => {
      const aVal = (a as Record<string, unknown>)[sort.key];
      const bVal = (b as Record<string, unknown>)[sort.key];
      if (aVal == null) return 1;
      if (bVal == null) return -1;
      const cmp = String(aVal).localeCompare(String(bVal), "ja");
      return sort.direction === "asc" ? cmp : -cmp;
    });
  }, [data, sort, onSortChange]);

  // ページネーション計算
  const isServerPagination = onPageChange !== undefined;
  const total = totalItems ?? data.length;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const page = isServerPagination ? currentPage : 0;

  // クライアントサイドページネーション
  const pagedData = useMemo(() => {
    if (isServerPagination) return sortedData;
    const start = page * pageSize;
    return sortedData.slice(start, start + pageSize);
  }, [sortedData, page, pageSize, isServerPagination]);

  // 全選択/全解除
  const allSelected =
    pagedData.length > 0 &&
    pagedData.every((row) => selectedKeys.includes(rowKey(row)));

  const handleSelectAll = () => {
    if (allSelected) {
      onSelectionChange?.([]);
    } else {
      onSelectionChange?.(pagedData.map((row) => rowKey(row)));
    }
  };

  const handleSelectRow = (key: string) => {
    if (selectedKeys.includes(key)) {
      onSelectionChange?.(selectedKeys.filter((k) => k !== key));
    } else {
      onSelectionChange?.([...selectedKeys, key]);
    }
  };

  // ソートアイコンを取得する
  const getSortIcon = (key: string) => {
    if (sort?.key !== key) return <ArrowUpDown className="ml-1 h-3 w-3" />;
    return sort.direction === "asc" ? (
      <ArrowUp className="ml-1 h-3 w-3" />
    ) : (
      <ArrowDown className="ml-1 h-3 w-3" />
    );
  };

  return (
    <div className="space-y-4">
      {/* テーブル本体 */}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              {/* 選択チェックボックス列 */}
              {selectable && (
                <TableHead className="w-12">
                  <input
                    type="checkbox"
                    checked={allSelected}
                    onChange={handleSelectAll}
                    className="h-4 w-4"
                  />
                </TableHead>
              )}
              {columns.map((col) => (
                <TableHead key={col.key} className={col.headerClassName}>
                  {col.sortable ? (
                    <button
                      className="flex items-center hover:text-foreground"
                      onClick={() => handleSort(col.key)}
                    >
                      {col.label}
                      {getSortIcon(col.key)}
                    </button>
                  ) : (
                    col.label
                  )}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {/* ローディング表示 */}
            {loading &&
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={`skeleton-${i}`}>
                  {selectable && (
                    <TableCell>
                      <Skeleton className="h-4 w-4" />
                    </TableCell>
                  )}
                  {columns.map((col) => (
                    <TableCell key={col.key}>
                      <Skeleton className="h-4 w-full" />
                    </TableCell>
                  ))}
                </TableRow>
              ))}

            {/* データなし表示 */}
            {!loading && pagedData.length === 0 && (
              <TableRow>
                <TableCell
                  colSpan={columns.length + (selectable ? 1 : 0)}
                  className="h-24 text-center text-muted-foreground"
                >
                  {emptyMessage}
                </TableCell>
              </TableRow>
            )}

            {/* データ行 */}
            {!loading &&
              pagedData.map((row) => {
                const key = rowKey(row);
                const isSelected = selectedKeys.includes(key);
                return (
                  <TableRow
                    key={key}
                    className={onRowClick ? "cursor-pointer" : ""}
                    data-state={isSelected ? "selected" : undefined}
                    onClick={() => onRowClick?.(row)}
                  >
                    {selectable && (
                      <TableCell>
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => handleSelectRow(key)}
                          onClick={(e) => e.stopPropagation()}
                          className="h-4 w-4"
                        />
                      </TableCell>
                    )}
                    {columns.map((col) => (
                      <TableCell key={col.key} className={col.cellClassName}>
                        {col.render
                          ? col.render(row)
                          : String(
                              (row as Record<string, unknown>)[col.key] ?? ""
                            )}
                      </TableCell>
                    ))}
                  </TableRow>
                );
              })}
          </TableBody>
        </Table>
      </div>

      {/* ページネーション */}
      {pagination && (
        <div className="flex items-center justify-between">
          {/* 表示件数セレクト */}
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <span>表示件数:</span>
            <Select
              value={String(pageSize)}
              onValueChange={(v) => setPageSize(Number(v))}
            >
              <SelectTrigger className="h-8 w-[80px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {pageSizeOptions.map((size) => (
                  <SelectItem key={size} value={String(size)}>
                    {size}件
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <span>/ 全 {total} 件</span>
          </div>

          {/* ページ送りボタン */}
          <div className="flex items-center gap-1">
            <Button
              variant="outline"
              size="icon"
              className="h-8 w-8"
              disabled={page === 0}
              onClick={() => onPageChange?.(page - 1)}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="px-2 text-sm">
              {page + 1} / {totalPages}
            </span>
            <Button
              variant="outline"
              size="icon"
              className="h-8 w-8"
              disabled={page >= totalPages - 1}
              onClick={() => onPageChange?.(page + 1)}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
