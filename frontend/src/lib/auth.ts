// ========================================
// JWT トークン管理
// ========================================

const TOKEN_KEY = "auth_token";

/** トークンを保存する */
export function setToken(token: string): void {
  if (typeof window !== "undefined") {
    localStorage.setItem(TOKEN_KEY, token);
  }
}

/** トークンを取得する */
export function getToken(): string | null {
  if (typeof window !== "undefined") {
    return localStorage.getItem(TOKEN_KEY);
  }
  return null;
}

/** トークンを削除する */
export function removeToken(): void {
  if (typeof window !== "undefined") {
    localStorage.removeItem(TOKEN_KEY);
  }
}

/** トークンが存在するか確認する */
export function hasToken(): boolean {
  return getToken() !== null;
}

/** JWTペイロードをデコードする（Base64デコード） */
export function decodeTokenPayload(
  token: string
): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = atob(parts[1]);
    return JSON.parse(payload);
  } catch {
    return null;
  }
}

/** トークンの有効期限を確認する */
export function isTokenExpired(token: string): boolean {
  const payload = decodeTokenPayload(token);
  if (!payload || typeof payload.exp !== "number") return true;
  // 現在時刻（秒）と比較して有効期限切れかどうかを判定
  return Date.now() >= payload.exp * 1000;
}

/** トークンが有効かどうかを確認する */
export function isAuthenticated(): boolean {
  const token = getToken();
  if (!token) return false;
  return !isTokenExpired(token);
}
