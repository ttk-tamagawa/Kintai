"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Clock, CalendarDays, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent } from "@/components/ui/sheet";

// ========================================
// サイドバーコンポーネント
// ナビゲーションリンクを表示する
// Desktop: 固定サイドバー / Mobile: Sheet（スライドイン）
// ========================================

/** ナビゲーション項目 */
const navItems = [
  {
    label: "勤怠管理",
    href: "/attendance",
    icon: Clock,
  },
  {
    label: "シフト管理",
    href: "/shifts",
    icon: CalendarDays,
  },
];

interface SidebarProps {
  /** モバイル時のサイドバー表示状態 */
  isOpen: boolean;
  /** サイドバーを閉じるハンドラー */
  onClose: () => void;
}

/** サイドバーのナビゲーション部分（Desktop/Mobile共通） */
function SidebarNav() {
  const pathname = usePathname();

  return (
    <nav className="flex flex-col gap-1 p-4">
      {navItems.map((item) => {
        // 現在のパスがリンク先と一致するかチェック
        const isActive = pathname.startsWith(item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            className={cn(
              "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
              isActive
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
            )}
          >
            <item.icon className="h-4 w-4" />
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}

export function Sidebar({ isOpen, onClose }: SidebarProps) {
  return (
    <>
      {/* Desktop用: 固定サイドバー */}
      <aside className="hidden lg:flex lg:w-60 lg:flex-col lg:border-r lg:bg-background">
        <div className="flex h-14 items-center border-b px-4">
          <span className="text-sm font-semibold text-muted-foreground">
            メニュー
          </span>
        </div>
        <SidebarNav />
      </aside>

      {/* Mobile用: スライドインのSheet */}
      <Sheet open={isOpen} onOpenChange={onClose}>
        <SheetContent side="left" className="w-60 p-0" showCloseButton={false}>
          <div className="flex h-14 items-center justify-between border-b px-4">
            <span className="text-sm font-semibold text-muted-foreground">
              メニュー
            </span>
            {/* ヘッダー内に配置して上下中央揃え */}
            <Button variant="ghost" size="icon" onClick={onClose}>
              <X className="h-4 w-4" />
            </Button>
          </div>
          {/* リンクをクリックしたらサイドバーを閉じる */}
          <div onClick={onClose}>
            <SidebarNav />
          </div>
        </SheetContent>
      </Sheet>
    </>
  );
}
