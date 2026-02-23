import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { TrendingUp, TrendingDown, Minus } from "lucide-react";

// ========================================
// KPICard コンポーネント
// ダッシュボード用の数値・トレンド表示カード
// ========================================

interface KpiCardProps {
  /** KPIのラベル（例: "総残業時間"） */
  label: string;
  /** メインの数値 */
  value: string | number;
  /** 単位（例: "時間", "日", "%"） */
  unit?: string;
  /** トレンド値（前月比などの差分。正=増加、負=減少） */
  trend?: number;
  /** トレンドの説明テキスト（例: "前月比"） */
  trendLabel?: string;
  /** サブテキスト（補足情報。例: "平均: 8.5時間"） */
  subText?: string;
  /** 追加クラス */
  className?: string;
}

export function KpiCard({
  label,
  value,
  unit,
  trend,
  trendLabel = "前月比",
  subText,
  className,
}: KpiCardProps) {
  // トレンドアイコンと色を決定する
  const getTrendDisplay = () => {
    if (trend === undefined || trend === null) return null;

    if (trend > 0) {
      return {
        icon: <TrendingUp className="h-3 w-3" />,
        className: "text-red-600",
        text: `+${trend}`,
      };
    }
    if (trend < 0) {
      return {
        icon: <TrendingDown className="h-3 w-3" />,
        className: "text-green-600",
        text: String(trend),
      };
    }
    return {
      icon: <Minus className="h-3 w-3" />,
      className: "text-muted-foreground",
      text: "0",
    };
  };

  const trendDisplay = getTrendDisplay();

  return (
    <Card className={cn("p-5", className)}>
      <CardContent className="p-0">
        {/* ラベル */}
        <p className="text-xs font-medium text-muted-foreground">{label}</p>

        {/* メイン数値 */}
        <div className="mt-2 flex items-baseline gap-1">
          <span className="text-2xl font-bold tabular-nums tracking-tight">
            {value}
          </span>
          {unit && (
            <span className="text-sm text-muted-foreground">{unit}</span>
          )}
        </div>

        {/* トレンド・サブテキスト */}
        <div className="mt-2 flex items-center gap-2">
          {trendDisplay && (
            <span
              className={cn(
                "flex items-center gap-0.5 text-xs font-medium",
                trendDisplay.className
              )}
            >
              {trendDisplay.icon}
              {trendDisplay.text} {trendLabel}
            </span>
          )}
          {subText && (
            <span className="text-xs text-muted-foreground">{subText}</span>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
