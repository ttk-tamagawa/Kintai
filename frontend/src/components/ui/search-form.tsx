"use client";

import type { ReactNode, FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Search, RotateCcw } from "lucide-react";

// ========================================
// SearchForm コンポーネント
// フィルター条件を入力して検索する汎用フォーム
// ========================================

interface SearchFormProps {
  /** フィルター入力要素（Selectや DatePicker等を渡す） */
  children: ReactNode;
  /** 検索ボタン押下時のハンドラー */
  onSearch: () => void;
  /** クリアボタン押下時のハンドラー */
  onClear: () => void;
  /** 検索中かどうか */
  loading?: boolean;
}

export function SearchForm({
  children,
  onSearch,
  onClear,
  loading = false,
}: SearchFormProps) {
  // フォーム送信時にページリロードを防ぐ
  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onSearch();
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="rounded-lg border bg-card p-4 shadow-sm"
    >
      {/* フィルター入力エリア */}
      <div className="flex flex-wrap items-end gap-4">{children}</div>

      {/* ボタンエリア */}
      <div className="mt-4 flex gap-2">
        <Button type="submit" size="sm" disabled={loading}>
          <Search className="mr-1 h-4 w-4" />
          {loading ? "検索中..." : "検索"}
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onClear}
          disabled={loading}
        >
          <RotateCcw className="mr-1 h-4 w-4" />
          クリア
        </Button>
      </div>
    </form>
  );
}
