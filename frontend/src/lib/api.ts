import axios, {
  AxiosError,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";
import {
  getRefreshToken,
  setToken,
  setRefreshToken,
  clearAllTokens,
} from "./auth";

// ========================================
// APIクライアント（Axiosラッパー）
// ========================================

/** APIベースURL */
const BASE_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api/v1";

/** Axiosインスタンス作成 */
const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 30000, // 30秒タイムアウト
});

// ========================================
// リクエストインターセプター
// ========================================

api.interceptors.request.use(
  (config) => {
    // JWT認証トークンをヘッダーに付与（トークンが存在する場合のみ）
    const token =
      typeof window !== "undefined" ? localStorage.getItem("auth_token") : null;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ========================================
// レスポンスインターセプター（リフレッシュトークン対応）
// ========================================

// リフレッシュ中かどうかを管理するフラグ
let isRefreshing = false;
// リフレッシュ完了待ちのリクエストキュー
let refreshSubscribers: Array<(token: string) => void> = [];

/** リフレッシュ完了後に待機中のリクエストを再実行する */
function onRefreshed(newToken: string) {
  refreshSubscribers.forEach((callback) => callback(newToken));
  refreshSubscribers = [];
}

/** リフレッシュ完了を待つPromiseを返す */
function addRefreshSubscriber(
  config: InternalAxiosRequestConfig
): Promise<AxiosResponse> {
  return new Promise((resolve) => {
    refreshSubscribers.push((newToken: string) => {
      config.headers.Authorization = `Bearer ${newToken}`;
      resolve(api(config));
    });
  });
}

api.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config;

    // 401 Unauthorized: トークンが無効または期限切れ
    if (error.response?.status === 401 && originalRequest) {
      // リフレッシュエンドポイント自体が401の場合は即ログアウト（無限ループ防止）
      if (originalRequest.url?.includes("/auth/refresh")) {
        clearAllTokens();
        if (
          typeof window !== "undefined" &&
          window.location.pathname !== "/login"
        ) {
          window.location.href = "/login";
        }
        return Promise.reject(error);
      }

      // リフレッシュトークンが存在しない場合は即ログアウト
      const currentRefreshToken = getRefreshToken();
      if (!currentRefreshToken) {
        clearAllTokens();
        if (
          typeof window !== "undefined" &&
          window.location.pathname !== "/login"
        ) {
          window.location.href = "/login";
        }
        return Promise.reject(error);
      }

      // 既にリフレッシュ中の場合は、完了待ちキューに追加する
      if (isRefreshing) {
        return addRefreshSubscriber(originalRequest);
      }

      // リフレッシュ処理を開始する
      isRefreshing = true;

      try {
        // リフレッシュトークンで新しいトークンペアを取得する
        const response = await api.post<{
          accessToken: string;
          refreshToken: string;
        }>("/auth/refresh", { refreshToken: currentRefreshToken });

        const { accessToken, refreshToken } = response.data;

        // 新しいトークンを保存する
        setToken(accessToken);
        setRefreshToken(refreshToken);

        // 待機中のリクエストを新しいトークンで再実行する
        onRefreshed(accessToken);

        // 元のリクエストを新しいトークンでリトライする
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return api(originalRequest);
      } catch {
        // リフレッシュ失敗: トークンをクリアしてログインページへ
        clearAllTokens();
        if (
          typeof window !== "undefined" &&
          window.location.pathname !== "/login"
        ) {
          window.location.href = "/login";
        }
        return Promise.reject(error);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

// ========================================
// API エラー型
// ========================================

/** RFC 7807 準拠のエラーレスポンス */
export interface ApiError {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  fieldErrors?: Record<string, string>;
}

/** AxiosErrorからAPIエラーを抽出するヘルパー */
export function extractApiError(error: unknown): ApiError {
  if (axios.isAxiosError(error) && error.response?.data) {
    return error.response.data as ApiError;
  }
  return {
    type: "about:blank",
    title: "通信エラー",
    status: 0,
    detail: "サーバーとの通信に失敗しました。ネットワーク接続を確認してください。",
  };
}

export default api;
