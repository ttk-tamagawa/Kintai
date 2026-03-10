"use client";

import { useMemo } from "react";
import { toast } from "sonner";

// ========================================
// Toast通知フック
// Sonnerライブラリのラッパー。統一的な通知を提供する
// useMemoで参照を安定化し、useCallback依存配列での無限ループを防止する
// ========================================

export function useToast() {
  return useMemo(
    () => ({
      /** 成功通知 */
      success: (message: string) => {
        toast.success(message);
      },

      /** エラー通知 */
      error: (message: string) => {
        toast.error(message);
      },

      /** 警告通知 */
      warning: (message: string) => {
        toast.warning(message);
      },

      /** 情報通知 */
      info: (message: string) => {
        toast.info(message);
      },

      /** API エラーを通知する（RFC 7807レスポンス対応） */
      apiError: (error: unknown) => {
        // AxiosErrorからメッセージを抽出して表示する
        const apiErr = error as { response?: { data?: { detail?: string } } };
        const message =
          apiErr?.response?.data?.detail ??
          "エラーが発生しました。しばらく経ってから再度お試しください。";
        toast.error(message);
      },
    }),
    []
  );
}
