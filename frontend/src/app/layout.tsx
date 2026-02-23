import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { Toaster } from "@/components/ui/sonner";
import { Providers } from "./providers";
import "./globals.css";

// ========================================
// ルートレイアウト
// フォント設定、認証プロバイダー、Toast通知の初期化
// ========================================

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "勤怠管理システム",
  description: "勤怠打刻・シフト管理・勤務時間集計",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ja">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        {/* 認証プロバイダーとレイアウトで全体をラップ */}
        <Providers>{children}</Providers>
        {/* Toast通知コンポーネント */}
        <Toaster position="top-right" richColors closeButton />
      </body>
    </html>
  );
}
