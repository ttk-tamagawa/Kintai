"use client";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

// ========================================
// ConfirmDialog コンポーネント
// 承認・削除・公開などの確認ダイアログ
// ========================================

interface ConfirmDialogProps {
  /** ダイアログの表示状態 */
  open: boolean;
  /** ダイアログを閉じるハンドラー */
  onClose: () => void;
  /** 確認ボタン押下時のハンドラー */
  onConfirm: () => void;
  /** タイトル */
  title: string;
  /** 確認メッセージ */
  description: string;
  /** 確認ボタンのラベル（デフォルト: "確認"） */
  confirmLabel?: string;
  /** キャンセルボタンのラベル（デフォルト: "キャンセル"） */
  cancelLabel?: string;
  /** 確認ボタンを危険色（赤）にするか */
  destructive?: boolean;
  /** 確認ボタンの処理中状態 */
  loading?: boolean;
}

export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  description,
  confirmLabel = "確認",
  cancelLabel = "キャンセル",
  destructive = false,
  loading = false,
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className="sm:max-w-[400px]">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <DialogFooter className="gap-2 sm:gap-0">
          <Button variant="outline" onClick={onClose} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button
            variant={destructive ? "destructive" : "default"}
            onClick={onConfirm}
            disabled={loading}
          >
            {loading ? "処理中..." : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
