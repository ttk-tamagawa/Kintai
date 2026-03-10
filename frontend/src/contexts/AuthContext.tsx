"use client";

import {
  createContext,
  useContext,
  useState,
  useCallback,
  useEffect,
  type ReactNode,
} from "react";
import { useRouter } from "next/navigation";
import type { AuthUser, Role } from "@/types";
import { getToken, clearAllTokens, setToken, decodeTokenPayload, isTokenExpired } from "@/lib/auth";

// ========================================
// 認証コンテキスト
// ログインユーザー情報をアプリ全体で共有する
// ========================================

interface AuthContextValue {
  /** 現在のログインユーザー（未認証時はnull） */
  user: AuthUser | null;
  /** ログイン処理（JWTトークンを受け取って保存） */
  login: (token: string) => void;
  /** ログアウト処理 */
  logout: () => void;
  /** 認証済みかどうか */
  isAuthenticated: boolean;
  /** 指定ロールを持っているか確認する */
  hasRole: (role: Role) => boolean;
  /** 指定ロールのいずれかを持っているか確認する */
  hasAnyRole: (roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/** JWTペイロードからユーザー情報を構築する */
function buildUserFromToken(token: string): AuthUser | null {
  const payload = decodeTokenPayload(token);
  if (!payload) return null;

  return {
    employeeId: (payload.employeeId as string) ?? "",
    name: (payload.name as string) ?? "",
    email: (payload.sub as string) ?? "",
    departmentId: (payload.departmentId as string) ?? "",
    departmentName: (payload.departmentName as string) ?? "",
    roles: (payload.roles as Role[]) ?? ["EMPLOYEE"],
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const router = useRouter();

  // 初回マウント時にlocalStorageのトークンからユーザー情報を復元する
  // トークンが期限切れの場合はクリアしてログイン画面に遷移する
  useEffect(() => {
    const token = getToken();
    if (token) {
      if (isTokenExpired(token)) {
        // 期限切れトークンをクリアしてログイン画面へ遷移する
        clearAllTokens();
        router.push("/login");
        return;
      }
      const restoredUser = buildUserFromToken(token);
      setUser(restoredUser);
    }
  }, [router]);

  // ログイン: トークンを保存してユーザー情報をセットする
  const login = useCallback((token: string) => {
    setToken(token);
    const newUser = buildUserFromToken(token);
    setUser(newUser);
  }, []);

  // ログアウト: トークンを削除してログイン画面に遷移する
  const logout = useCallback(() => {
    clearAllTokens();
    setUser(null);
    router.push("/login");
  }, [router]);

  // 指定ロールを持っているか確認する
  const hasRole = useCallback(
    (role: Role) => user?.roles.includes(role) ?? false,
    [user]
  );

  // 指定ロールのいずれかを持っているか確認する
  const hasAnyRole = useCallback(
    (roles: Role[]) => roles.some((role) => user?.roles.includes(role)),
    [user]
  );

  return (
    <AuthContext
      value={{
        user,
        login,
        logout,
        isAuthenticated: user !== null,
        hasRole,
        hasAnyRole,
      }}
    >
      {children}
    </AuthContext>
  );
}

/** 認証コンテキストを取得するフック */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth は AuthProvider の中で使用してください");
  }
  return context;
}
