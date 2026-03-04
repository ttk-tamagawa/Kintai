// ========================================
// JWT トークン管理
// ========================================

const TOKEN_KEY = "auth_token";
const REFRESH_TOKEN_KEY = "refresh_token";

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

/** リフレッシュトークンを保存する */
export function setRefreshToken(token: string): void {
  if (typeof window !== "undefined") {
    localStorage.setItem(REFRESH_TOKEN_KEY, token);
  }
}

/** リフレッシュトークンを取得する */
export function getRefreshToken(): string | null {
  if (typeof window !== "undefined") {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }
  return null;
}

/** リフレッシュトークンを削除する */
export function removeRefreshToken(): void {
  if (typeof window !== "undefined") {
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }
}

/** アクセストークンとリフレッシュトークンを両方削除する */
export function clearAllTokens(): void {
  removeToken();
  removeRefreshToken();
}

/** JWTペイロードをデコードする（Base64url → UTF-8デコード） */
export function decodeTokenPayload(
  token: string
): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    // Base64url → 標準Base64 に変換してデコード
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    // atob は Latin-1 しか扱えないため、UTF-8 バイト列を TextDecoder で正しく変換する
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const payload = new TextDecoder().decode(bytes);
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
