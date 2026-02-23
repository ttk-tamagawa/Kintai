import axios, { AxiosError, type AxiosResponse } from "axios";

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
// レスポンスインターセプター
// ========================================

api.interceptors.response.use(
  (response: AxiosResponse) => response,
  (error: AxiosError) => {
    // 401 Unauthorized: トークン無効 → ログインページへリダイレクト
    if (error.response?.status === 401) {
      if (typeof window !== "undefined") {
        localStorage.removeItem("auth_token");
        // 認証が実装されたらログインページへリダイレクトする
        // window.location.href = "/login";
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
