import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

// ========================================
// Vitest 設定
// React + jsdom 環境でコンポーネントテストを実行する
// ========================================
export default defineConfig({
  plugins: [react()],
  test: {
    // ブラウザ DOM をシミュレートする jsdom 環境を使用する
    environment: "jsdom",
    // テスト実行前に jest-dom マッチャーとポリフィルを読み込む
    setupFiles: ["./src/test/setup.ts"],
    // グローバルな describe / it / expect を有効にする
    globals: true,
  },
  resolve: {
    alias: {
      // tsconfig.json の @/* パスエイリアスと一致させる
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
