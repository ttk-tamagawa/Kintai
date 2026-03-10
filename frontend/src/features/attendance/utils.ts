import { format, parseISO } from "date-fns";
import { ja } from "date-fns/locale";

// ========================================
// 勤怠管理画面 共通ユーティリティ
// 日付・時刻・時間のフォーマット関数群
// ========================================

/** ISO 8601 日時文字列から時刻を "HH:mm" 形式で表示する */
export function formatTime(isoString: string | null): string {
  if (!isoString) return "--:--";
  return format(parseISO(isoString), "HH:mm");
}

/** ISO 8601 日付文字列を "MM/dd(曜)" 形式で表示する（例: "01/15(水)"） */
export function formatWorkDate(dateString: string): string {
  return format(parseISO(dateString), "MM/dd(E)", { locale: ja });
}

/** 分数を "H:mm" 形式の文字列に変換する（例: 478分 → "7:58"） */
export function formatMinutes(minutes: number | null | undefined): string {
  if (minutes == null || minutes < 0) return "--:--";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${h}:${String(m).padStart(2, "0")}`;
}

/** 時間数を小数第1位つき文字列にフォーマットする（例: 176.0） */
export function formatHours(hours: number): string {
  return hours.toFixed(1);
}

/** 現在の年月を "YYYY-MM" 形式で取得する */
export function getCurrentMonth(): string {
  return format(new Date(), "yyyy-MM");
}

/** 当月1日を "YYYY-MM-DD" 形式で取得する */
export function getMonthStart(): string {
  const now = new Date();
  return format(new Date(now.getFullYear(), now.getMonth(), 1), "yyyy-MM-dd");
}

/** 当月末日を "YYYY-MM-DD" 形式で取得する */
export function getMonthEnd(): string {
  const now = new Date();
  return format(
    new Date(now.getFullYear(), now.getMonth() + 1, 0),
    "yyyy-MM-dd"
  );
}

/** 現在時刻を ISO 8601 形式で取得する（打刻API送信用） */
export function getNowISO(): string {
  return new Date().toISOString();
}
