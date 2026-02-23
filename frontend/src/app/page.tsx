import { redirect } from "next/navigation";

// ========================================
// トップページ
// 勤怠管理ページへリダイレクトする
// ========================================

export default function Home() {
  redirect("/attendance");
}
