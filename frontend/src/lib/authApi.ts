import api from "./api";

// ========================================
// 認証 API 関数
// ========================================

/** 開発用メールログイン — email だけでJWTトークンを取得する */
export async function devLogin(email: string): Promise<string> {
  const response = await api.post<{ token: string }>("/auth/dev-login", {
    email,
  });
  return response.data.token;
}

/** Google OAuth ログイン — Google ID トークンでJWTを取得する */
export async function googleLogin(idToken: string): Promise<string> {
  const response = await api.post<{ token: string }>("/auth/google", {
    idToken,
  });
  return response.data.token;
}
