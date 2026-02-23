import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

// ========================================
// StatusBadge コンポーネント
// ステータスに応じた色分けバッジを表示する
// ========================================

/** ステータスごとの色とラベルの定義 */
const statusConfig: Record<
  string,
  { label: string; className: string }
> = {
  // 勤怠ステータス
  NOT_CLOCKED: {
    label: "未出勤",
    className: "bg-gray-100 text-gray-700 hover:bg-gray-100",
  },
  CLOCKED_IN: {
    label: "出勤中",
    className: "bg-blue-100 text-blue-700 hover:bg-blue-100",
  },
  ON_BREAK: {
    label: "休憩中",
    className: "bg-amber-100 text-amber-700 hover:bg-amber-100",
  },
  CLOCKED_OUT: {
    label: "退勤済",
    className: "bg-green-100 text-green-700 hover:bg-green-100",
  },
  FINALIZED: {
    label: "確定済",
    className: "bg-purple-100 text-purple-700 hover:bg-purple-100",
  },
  // シフトステータス
  DRAFT: {
    label: "下書き",
    className: "bg-yellow-100 text-yellow-700 hover:bg-yellow-100",
  },
  PUBLISHED: {
    label: "公開済",
    className: "bg-green-100 text-green-700 hover:bg-green-100",
  },
  // シフトパターン有効/無効
  ACTIVE: {
    label: "有効",
    className: "bg-green-100 text-green-700 hover:bg-green-100",
  },
  INACTIVE: {
    label: "無効",
    className: "bg-gray-100 text-gray-500 hover:bg-gray-100",
  },
};

interface StatusBadgeProps {
  /** ステータスキー */
  status: string;
  /** カスタムラベル（省略時はstatusConfigのラベルを使用） */
  label?: string;
  /** 追加クラス */
  className?: string;
}

export function StatusBadge({ status, label, className }: StatusBadgeProps) {
  const config = statusConfig[status];

  // 定義されていないステータスの場合はグレーで表示
  const displayLabel = label ?? config?.label ?? status;
  const badgeClassName = config?.className ?? "bg-gray-100 text-gray-600 hover:bg-gray-100";

  return (
    <Badge
      variant="secondary"
      className={cn("text-xs font-medium", badgeClassName, className)}
    >
      {displayLabel}
    </Badge>
  );
}
