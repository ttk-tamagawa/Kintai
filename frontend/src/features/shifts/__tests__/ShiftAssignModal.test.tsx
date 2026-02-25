import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ShiftAssignModal } from "../ShiftAssignModal";
import type { ScheduleItem, ShiftPattern } from "@/types";

// ========================================
// ShiftAssignModal コンポーネントテスト（9-3-3）
// バリデーション、モード切替（作成/編集）を検証する
// ========================================

// --- モック定義 ---

// Toast モック
const mockToast = {
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  info: vi.fn(),
  apiError: vi.fn(),
};
vi.mock("@/hooks/useToast", () => ({
  useToast: () => mockToast,
}));

// API モック
const mockFetchActivePatterns = vi.fn();
const mockFetchEmployees = vi.fn();
const mockCreateSchedule = vi.fn();
const mockUpdateSchedule = vi.fn();
const mockPublishSchedule = vi.fn();

vi.mock("../api", () => ({
  fetchActivePatterns: (...args: unknown[]) =>
    mockFetchActivePatterns(...args),
  fetchEmployees: (...args: unknown[]) => mockFetchEmployees(...args),
  createSchedule: (...args: unknown[]) => mockCreateSchedule(...args),
  updateSchedule: (...args: unknown[]) => mockUpdateSchedule(...args),
  publishSchedule: (...args: unknown[]) => mockPublishSchedule(...args),
}));

// --- テスト用データ ---

/** テスト用パターン */
const testPatterns: ShiftPattern[] = [
  {
    patternId: "pat-001",
    name: "早番",
    startTime: "08:00",
    endTime: "16:00",
    breakMinutes: 60,
    isOvernight: false,
    isActive: true,
    createdAt: "2026-01-01T00:00:00",
  },
  {
    patternId: "pat-002",
    name: "遅番",
    startTime: "14:00",
    endTime: "22:00",
    breakMinutes: 60,
    isOvernight: false,
    isActive: true,
    createdAt: "2026-01-01T00:00:00",
  },
];

/** テスト用従業員 */
const testEmployees = [
  { employeeId: "emp-001", employeeName: "田中太郎" },
  { employeeId: "emp-002", employeeName: "鈴木花子" },
];

/** 編集用の DRAFT スケジュール */
const draftSchedule: ScheduleItem = {
  scheduleId: "sch-001",
  employeeId: "emp-001",
  employeeName: "田中太郎",
  weekStartDate: "2026-02-23",
  status: "DRAFT",
  assignments: {
    MONDAY: {
      patternId: "pat-001",
      patternName: "早番",
      startTime: "08:00",
      endTime: "16:00",
    },
  },
};

/** 編集用の PUBLISHED スケジュール */
const publishedSchedule: ScheduleItem = {
  ...draftSchedule,
  scheduleId: "sch-002",
  status: "PUBLISHED",
};

// --- テスト本体 ---

/** モーダルをレンダリングするヘルパー */
function renderModal(
  props?: Partial<React.ComponentProps<typeof ShiftAssignModal>>
) {
  const defaultProps = {
    open: true,
    mode: "create" as const,
    editTarget: null,
    onClose: vi.fn(),
    onComplete: vi.fn(),
  };
  return render(<ShiftAssignModal {...defaultProps} {...props} />);
}

describe("ShiftAssignModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // デフォルト: パターンと従業員をモック返却する
    mockFetchActivePatterns.mockResolvedValue(testPatterns);
    mockFetchEmployees.mockResolvedValue(testEmployees);
  });

  // ========================================
  // 1. 作成モード: タイトルが「シフト割当」で表示される
  // ========================================
  it("作成モードでは「シフト割当」タイトルが表示される", async () => {
    renderModal({ mode: "create" });

    // 「シフト割当」はタイトルとラベルの2箇所に存在するため heading ロールで取得する
    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "シフト割当" })
      ).toBeInTheDocument();
    });
  });

  // ========================================
  // 2. 編集モード: タイトルが「シフト変更」で表示される
  // ========================================
  it("編集モードでは「シフト変更」タイトルが表示される", async () => {
    renderModal({ mode: "edit", editTarget: draftSchedule });

    await waitFor(() => {
      expect(screen.getByText("シフト変更")).toBeInTheDocument();
    });
  });

  // ========================================
  // 3. 編集モード: 従業員名と週開始日が読取専用で表示される
  // ========================================
  it("編集モードでは従業員名と週開始日が読取専用で表示される", async () => {
    renderModal({ mode: "edit", editTarget: draftSchedule });

    await waitFor(() => {
      expect(screen.getByText("田中太郎")).toBeInTheDocument();
      expect(screen.getByText("2026-02-23")).toBeInTheDocument();
    });
  });

  // ========================================
  // 4. 編集 + PUBLISHED 状態: 警告メッセージが表示される
  // ========================================
  it("PUBLISHED 状態では「下書きに戻ります」警告が表示される", async () => {
    renderModal({ mode: "edit", editTarget: publishedSchedule });

    await waitFor(() => {
      expect(
        screen.getByText("変更すると下書きに戻ります")
      ).toBeInTheDocument();
    });
  });

  // ========================================
  // 5. 編集 + DRAFT: 公開ボタンが表示される
  // ========================================
  it("DRAFT 状態の編集モードでは公開ボタンが表示される", async () => {
    renderModal({ mode: "edit", editTarget: draftSchedule });

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "公開する" })
      ).toBeInTheDocument();
    });
  });

  // ========================================
  // 6. 編集 + PUBLISHED: 公開ボタンが非表示
  // ========================================
  it("PUBLISHED 状態の編集モードでは公開ボタンが表示されない", async () => {
    renderModal({ mode: "edit", editTarget: publishedSchedule });

    await waitFor(() => {
      expect(screen.getByText("シフト変更")).toBeInTheDocument();
    });

    // 公開ボタンが存在しないことを確認する
    expect(
      screen.queryByRole("button", { name: "公開する" })
    ).not.toBeInTheDocument();
  });

  // ========================================
  // 7. 作成モードでバリデーション: 割当なしエラー
  // ========================================
  it("シフト未割当で送信すると「少なくとも1日はシフトを割り当ててください」エラーが表示される", async () => {
    const user = userEvent.setup();
    renderModal({ mode: "create" });

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "割当" })
      ).toBeInTheDocument();
    });

    // 何も入力せずに送信ボタンをクリックする
    await user.click(screen.getByRole("button", { name: "割当" }));

    // バリデーションエラーが表示されることを確認する
    await waitFor(() => {
      expect(
        screen.getByText("少なくとも1日はシフトを割り当ててください")
      ).toBeInTheDocument();
    });
  });

  // ========================================
  // 8. 作成モードでバリデーション: 従業員未選択エラー
  // ========================================
  it("従業員未選択で送信すると「従業員を選択してください」エラーが表示される", async () => {
    const user = userEvent.setup();
    renderModal({ mode: "create" });

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "割当" })
      ).toBeInTheDocument();
    });

    // 送信ボタンをクリックする
    await user.click(screen.getByRole("button", { name: "割当" }));

    // 従業員の必須エラーが表示されることを確認する
    await waitFor(() => {
      expect(
        screen.getByText("従業員を選択してください")
      ).toBeInTheDocument();
    });
  });

  // ========================================
  // 9. open=false のときモーダルが表示されない
  // ========================================
  it("open=false のときモーダルコンテンツが表示されない", () => {
    renderModal({ open: false });

    expect(screen.queryByText("シフト割当")).not.toBeInTheDocument();
  });

  // ========================================
  // 10. 編集モードでフッターに「変更」ボタンが表示される
  // ========================================
  it("編集モードでは送信ボタンのラベルが「変更」になる", async () => {
    renderModal({ mode: "edit", editTarget: draftSchedule });

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "変更" })
      ).toBeInTheDocument();
    });
  });
});
