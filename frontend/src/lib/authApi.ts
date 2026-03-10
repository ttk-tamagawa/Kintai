import api from "./api";

// ========================================
// 認証 API 関数
// ========================================

/** 認証レスポンス型（アクセストークン + リフレッシュトークン） */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}

/** 開発用メールログイン — email だけでトークンペアを取得する */
export async function devLogin(email: string): Promise<AuthResponse> {
  const response = await api.post<AuthResponse>("/auth/dev-login", {
    email,
  });
  return response.data;
}

/** Google OAuth ログイン — Google ID トークンでトークンペアを取得する */
export async function googleLogin(idToken: string): Promise<AuthResponse> {
  const response = await api.post<AuthResponse>("/auth/google", {
    idToken,
  });
  return response.data;
}

/** リフレッシュトークンで新しいトークンペアを取得する */
export async function refreshTokens(
  refreshToken: string
): Promise<AuthResponse> {
  const response = await api.post<AuthResponse>("/auth/refresh", {
    refreshToken,
  });
  return response.data;
}
