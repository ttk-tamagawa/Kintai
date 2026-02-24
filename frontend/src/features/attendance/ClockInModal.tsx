"use client";

import { useState, useEffect, useCallback } from "react";
import { format } from "date-fns";
import { ja } from "date-fns/locale";
import { Loader2 } from "lucide-react";
import { Modal } from "@/components/ui/modal";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/contexts/AuthContext";
import { useToast } from "@/hooks/useToast";
import {
  fetchTodayAttendance,
  clockIn,
  clockOut,
  breakStart,
  breakEnd,
} from "./api";
import { formatTime, formatMinutes, getNowISO } from "./utils";
import type { TodayAttendance, AttendanceStatus } from "@/types";

// ========================================
// 勤怠打刻モーダル（SCR-ATT-001）
// リアルタイム時計・ステータスバッジ・4ボタンマトリクス・日次サマリー
// ========================================

/** 画面上の表示用ステータス（ON_BREAKはCLOCKED_IN+onBreakから導出） */
type DisplayStatus = AttendanceStatus | "ON_BREAK";

interface ClockInModalProps {
  /** モーダル表示状態 */
  open: boolean;
  /** モーダルを閉じるハンドラー */
  onClose: () => void;
  /** 打刻後に親画面を更新するコールバック */
  onClockAction?: () => void;
}

export function ClockInModal({
  open,
  onClose,
  onClockAction,
}: ClockInModalProps) {
  const { user } = useAuth();
  const toast = useToast();

  // 現在時刻（1秒ごと更新）
  const [currentTime, setCurrentTime] = useState(new Date());
  // 本日の勤怠データ
  const [attendance, setAttendance] = useState<TodayAttendance | null>(null);
  // データ読み込み中
  const [loading, setLoading] = useState(false);
  // 打刻処理中（二重送信防止）
  const [submitting, setSubmitting] = useState(false);

  // ========================================
  // リアルタイム時計（1秒更新、コンポーネント破棄時にクリア）
  // ========================================
  useEffect(() => {
    if (!open) return;
    const timer = setInterval(() => setCurrentTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, [open]);

  // ========================================
  // モーダル表示時に本日の勤怠情報を取得する
  // ========================================
  const loadToday = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchTodayAttendance();
      setAttendance(data);
    } catch (err) {
      toast.apiError(err);
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    if (open) loadToday();
  }, [open, loadToday]);

  // ========================================
  // 表示用ステータスを導出する
  // ========================================
  const displayStatus: DisplayStatus = (() => {
    if (!attendance) return "NOT_CLOCKED";
    if (attendance.status === "CLOCKED_IN" && attendance.onBreak)
      return "ON_BREAK";
    return attendance.status;
  })();

  // ========================================
  // ボタン有効/無効マトリクス（設計書準拠）
  // ========================================
  const canClockIn = displayStatus === "NOT_CLOCKED";
  const canClockOut =
    displayStatus === "CLOCKED_IN"; // 休憩中は退勤不可
  const canBreakStart =
    displayStatus === "CLOCKED_IN"; // 休憩中でない出勤中のみ
  const canBreakEnd = displayStatus === "ON_BREAK";

  // ========================================
  // 打刻アクション共通処理
  // ========================================
  const handleAction = async (
    action: () => Promise<unknown>,
    successMessage: string
  ) => {
    if (!user || submitting) return;
    setSubmitting(true);
    try {
      await action();
      toast.success(successMessage);
      // 打刻後にステータスを再取得する
      await loadToday();
      onClockAction?.();
    } catch (err) {
      toast.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const handleClockIn = () =>
    handleAction(
      () => clockIn(user!.employeeId, getNowISO()),
      "出勤を記録しました"
    );

  const handleClockOut = () =>
    handleAction(
      () => clockOut(user!.employeeId, getNowISO()),
      "退勤を記録しました"
    );

  const handleBreakStart = () =>
    handleAction(
      () => breakStart(user!.employeeId, getNowISO()),
      "休憩を開始しました"
    );

  const handleBreakEnd = () =>
    handleAction(
      () => breakEnd(user!.employeeId, getNowISO()),
      "休憩を終了しました"
    );

  // ========================================
  // 勤務時間のリアルタイム計算（出勤中のみ）
  // ========================================
  const getElapsedWorkMinutes = (): number | null => {
    if (!attendance?.clockIn) return null;
    if (attendance.status === "CLOCKED_OUT" || attendance.status === "FINALIZED")
      return attendance.netWorkMinutes;
    // 出勤中: 現在時刻 - 出勤時刻 - 休憩時間
    const clockInTime = new Date(attendance.clockIn).getTime();
    const elapsed = Math.floor((Date.now() - clockInTime) / 60000);
    return Math.max(0, elapsed - (attendance.breakMinutes ?? 0));
  };

  // ========================================
  // Primary ボタンの判定（期待される操作を強調表示）
  // ========================================
  const isPrimary = (action: string): boolean => {
    if (action === "clockIn" && canClockIn) return true;
    if (action === "breakEnd" && canBreakEnd) return true;
    return false;
  };

  return (
    <Modal open={open} onClose={onClose} title="勤怠打刻">
      {loading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : (
        <div className="space-y-4">
          {/* ステータスセクション: バッジ + 時計 + 日付 */}
          <div className="flex flex-col items-center gap-2 border-b pb-4">
            <StatusBadge status={displayStatus} />
            <span className="font-mono text-4xl font-bold tracking-tight">
              {format(currentTime, "HH:mm:ss")}
            </span>
            <span className="text-sm text-muted-foreground">
              {format(currentTime, "yyyy-MM-dd (E)", { locale: ja })}
            </span>
          </div>

          {/* アクションセクション: 2×2 ボタングリッド */}
          <div className="grid grid-cols-2 gap-3">
            <Button
              className="h-12"
              variant={isPrimary("clockIn") ? "default" : "outline"}
              disabled={!canClockIn || submitting}
              onClick={handleClockIn}
            >
              {submitting && canClockIn && (
                <Loader2 className="mr-1 h-4 w-4 animate-spin" />
              )}
              出勤
            </Button>
            <Button
              className="h-12"
              variant="outline"
              disabled={!canClockOut || submitting}
              onClick={handleClockOut}
            >
              {submitting && canClockOut && (
                <Loader2 className="mr-1 h-4 w-4 animate-spin" />
              )}
              退勤
            </Button>
            <Button
              className="h-12"
              variant="outline"
              disabled={!canBreakStart || submitting}
              onClick={handleBreakStart}
            >
              {submitting && canBreakStart && (
                <Loader2 className="mr-1 h-4 w-4 animate-spin" />
              )}
              休憩開始
            </Button>
            <Button
              className="h-12"
              variant={isPrimary("breakEnd") ? "default" : "outline"}
              disabled={!canBreakEnd || submitting}
              onClick={handleBreakEnd}
            >
              {submitting && canBreakEnd && (
                <Loader2 className="mr-1 h-4 w-4 animate-spin" />
              )}
              休憩終了
            </Button>
          </div>

          {/* 日次サマリーセクション: 本日の実績 */}
          <div className="rounded-lg bg-gray-50 p-4">
            <p className="mb-3 text-xs font-medium text-muted-foreground">
              本日の実績
            </p>
            <div className="space-y-2">
              {/* 出勤時刻 */}
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">出勤時刻</span>
                <span className="font-mono font-semibold">
                  {formatTime(attendance?.clockIn ?? null)}
                </span>
              </div>
              {/* 退勤時刻（退勤後のみ表示） */}
              {(displayStatus === "CLOCKED_OUT" ||
                displayStatus === "FINALIZED") && (
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">退勤時刻</span>
                  <span className="font-mono font-semibold">
                    {formatTime(attendance?.clockOut ?? null)}
                  </span>
                </div>
              )}
              {/* 休憩時間 */}
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">休憩時間</span>
                <span className="font-mono font-semibold">
                  {formatMinutes(attendance?.breakMinutes ?? null)}
                  {displayStatus === "ON_BREAK" && (
                    <span className="ml-1 text-xs text-muted-foreground">
                      (経過)
                    </span>
                  )}
                </span>
              </div>
              {/* 勤務時間 */}
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">勤務時間</span>
                <span className="font-mono font-semibold">
                  {formatMinutes(getElapsedWorkMinutes())}
                  {displayStatus === "CLOCKED_IN" && (
                    <span className="ml-1 text-xs text-muted-foreground">
                      (経過)
                    </span>
                  )}
                  {(displayStatus === "CLOCKED_OUT" ||
                    displayStatus === "FINALIZED") && (
                    <span className="ml-1 text-xs text-muted-foreground">
                      (実働)
                    </span>
                  )}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}
