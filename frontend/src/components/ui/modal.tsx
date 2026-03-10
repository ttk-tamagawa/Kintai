"use client";

import type { ReactNode } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

// ========================================
// Modal コンポーネント
// ヘッダー・ボディ・フッターの3エリア構成
// Desktop: 中央配置 / Mobile: フルスクリーンスライドアップ
// ========================================

interface ModalProps {
  /** モーダルの表示状態 */
  open: boolean;
  /** モーダルを閉じるハンドラー */
  onClose: () => void;
  /** モーダルタイトル */
  title: string;
  /** モーダルの説明テキスト（省略可） */
  description?: string;
  /** モーダル本文コンテンツ */
  children: ReactNode;
  /** フッターコンテンツ（ボタン等） */
  footer?: ReactNode;
  /** モーダルの幅クラス（デフォルト: sm:max-w-[450px]） */
  className?: string;
}

export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  className = "sm:max-w-[450px]",
}: ModalProps) {
  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className={className}>
        {/* ヘッダー: タイトルと説明 */}
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && (
            <DialogDescription>{description}</DialogDescription>
          )}
        </DialogHeader>

        {/* ボディ: メインコンテンツ */}
        <div className="py-4">{children}</div>

        {/* フッター: アクションボタン */}
        {footer && <DialogFooter>{footer}</DialogFooter>}
      </DialogContent>
    </Dialog>
  );
}
