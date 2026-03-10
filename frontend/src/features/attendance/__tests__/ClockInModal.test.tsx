import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ClockInModal } from "../ClockInModal";

// ========================================
// ClockInModal コンポーネントテスト（9-3-1）
// ボタン有効/無効の状態制御、API 呼び出しを検証する
// ========================================

// --- モック定義 ---

// 認証コンテキストのモック（ログイン済みユーザーを返す）
const mockUser = {
  employeeId: "emp-001",
  name: "テスト太郎",
  email: "test@example.com",
  departmentId: "dept-001",
  departmentName: "開発部",
  roles: ["EMPLOYEE" as const],
};

vi.mock("@/contexts/AuthContext", () => ({
  useAuth: () => ({
    user: mockUser,
    isAuthenticated: true,
    hasRole: () => false,
    hasAnyRole: () => false,
  }),
}));

// Toast 通知のモック
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

// API 関数のモック
const mockFetchTodayAttendance = vi.fn();
const mockClockIn = vi.fn();
const mockClockOut = vi.fn();
const mockBreakStart = vi.fn();
const mockBreakEnd = vi.fn();

vi.mock("../api", () => ({
  fetchTodayAttendance: (...args: unknown[]) =>
    mockFetchTodayAttendance(...args),
  clockIn: (...args: unknown[]) => mockClockIn(...args),
  clockOut: (...args: unknown[]) => mockClockOut(...args),
  breakStart: (...args: unknown[]) => mockBreakStart(...args),
  breakEnd: (...args: unknown[]) => mockBreakEnd(...args),
}));

// ユーティリティ関数のモック
vi.mock("../utils", () => ({
  formatTime: (v: string | null) => v ?? "--:--",
  formatMinutes: (v: number | null) => (v != null ? `${v}分` : "--:--"),
  getNowISO: () => "2026-02-25T09:00:00.000Z",
}));

// --- テスト本体 ---

/** モーダルをレンダリングするヘルパー */
function renderModal(props?: Partial<React.ComponentProps<typeof ClockInModal>>) {
  const defaultProps = {
    open: true,
    onClose: vi.fn(),
    onClockAction: vi.fn(),
  };
  return render(<ClockInModal {...defaultProps} {...props} />);
}

describe("ClockInModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ========================================
  // 1. NOT_CLOCKED 状態: 出勤ボタンのみ有効
  // ========================================
  it("NOT_CLOCKED 状態では出勤ボタンのみ有効になる", async () => {
    // 未出勤: fetchTodayAttendance が null を返す
    mockFetchTodayAttendance.mockResolvedValue(null);

    renderModal();

    // データ読み込み完了を待つ
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /出勤/ })).toBeInTheDocument();
    });

    // 出勤ボタンは有効
    expect(screen.getByRole("button", { name: /出勤/ })).toBeEnabled();

    // 退勤・休憩開始・休憩終了は無効
    expect(screen.getByRole("button", { name: /退勤/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /休憩開始/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /休憩終了/ })).toBeDisabled();
  });

  // ========================================
  // 2. CLOCKED_IN 状態: 退勤・休憩開始が有効
  // ========================================
  it("CLOCKED_IN 状態では退勤・休憩開始ボタンが有効になる", async () => {
    mockFetchTodayAttendance.mockResolvedValue({
      attendanceId: "att-001",
      employeeId: "emp-001",
      workDate: "2026-02-25",
      status: "CLOCKED_IN",
      clockIn: "2026-02-25T09:00:00",
      clockOut: null,
      breakMinutes: 0,
      netWorkMinutes: 0,
      overtimeMinutes: 0,
      onBreak: false,
    });

    renderModal();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /退勤/ })).toBeEnabled();
    });

    // 出勤は無効（既に出勤済み）
    expect(screen.getByRole("button", { name: /出勤/ })).toBeDisabled();
    // 退勤・休憩開始は有効
    expect(screen.getByRole("button", { name: /退勤/ })).toBeEnabled();
    expect(screen.getByRole("button", { name: /休憩開始/ })).toBeEnabled();
    // 休憩終了は無効（休憩中ではない）
    expect(screen.getByRole("button", { name: /休憩終了/ })).toBeDisabled();
  });

  // ========================================
  // 3. ON_BREAK 状態: 休憩終了のみ有効
  // ========================================
  it("ON_BREAK 状態では休憩終了ボタンのみ有効になる", async () => {
    mockFetchTodayAttendance.mockResolvedValue({
      attendanceId: "att-001",
      employeeId: "emp-001",
      workDate: "2026-02-25",
      status: "CLOCKED_IN",
      clockIn: "2026-02-25T09:00:00",
      clockOut: null,
      breakMinutes: 15,
      netWorkMinutes: 0,
      overtimeMinutes: 0,
      onBreak: true, // 休憩中フラグ
    });

    renderModal();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /休憩終了/ })).toBeEnabled();
    });

    // 出勤・退勤・休憩開始は無効
    expect(screen.getByRole("button", { name: /出勤/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /退勤/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /休憩開始/ })).toBeDisabled();
  });

  // ========================================
  // 4. CLOCKED_OUT 状態: 全ボタン無効
  // ========================================
  it("CLOCKED_OUT 状態では全ボタンが無効になる", async () => {
    mockFetchTodayAttendance.mockResolvedValue({
      attendanceId: "att-001",
      employeeId: "emp-001",
      workDate: "2026-02-25",
      status: "CLOCKED_OUT",
      clockIn: "2026-02-25T09:00:00",
      clockOut: "2026-02-25T18:00:00",
      breakMinutes: 60,
      netWorkMinutes: 480,
      overtimeMinutes: 0,
      onBreak: false,
    });

    renderModal();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /出勤/ })).toBeDisabled();
    });

    // 全ボタンが無効
    expect(screen.getByRole("button", { name: /退勤/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /休憩開始/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /休憩終了/ })).toBeDisabled();
  });

  // ========================================
  // 5. 出勤ボタンクリックで clockIn API が呼ばれる
  // ========================================
  it("出勤ボタンクリックで clockIn API が呼ばれる", async () => {
    const user = userEvent.setup();
    mockFetchTodayAttendance.mockResolvedValue(null);
    mockClockIn.mockResolvedValue({
      attendanceId: "att-001",
      employeeId: "emp-001",
      workDate: "2026-02-25",
      status: "CLOCKED_IN",
    });

    const onClockAction = vi.fn();
    renderModal({ onClockAction });

    // 出勤ボタンが表示されるまで待つ
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /出勤/ })).toBeEnabled();
    });

    // 出勤ボタンをクリックする
    await user.click(screen.getByRole("button", { name: /出勤/ }));

    // clockIn API が正しいパラメータで呼ばれたことを確認する
    await waitFor(() => {
      expect(mockClockIn).toHaveBeenCalledWith(
        "emp-001",
        "2026-02-25T09:00:00.000Z"
      );
    });

    // 成功トースト表示を確認する
    await waitFor(() => {
      expect(mockToast.success).toHaveBeenCalledWith("出勤を記録しました");
    });
  });

  // ========================================
  // 6. 退勤ボタンクリックで clockOut API が呼ばれる
  // ========================================
  it("退勤ボタンクリックで clockOut API が呼ばれる", async () => {
    const user = userEvent.setup();
    mockFetchTodayAttendance.mockResolvedValue({
      attendanceId: "att-001",
      employeeId: "emp-001",
      workDate: "2026-02-25",
      status: "CLOCKED_IN",
      clockIn: "2026-02-25T09:00:00",
      clockOut: null,
      breakMinutes: 0,
      netWorkMinutes: 0,
      overtimeMinutes: 0,
      onBreak: false,
    });
    mockClockOut.mockResolvedValue({});

    renderModal();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /退勤/ })).toBeEnabled();
    });

    await user.click(screen.getByRole("button", { name: /退勤/ }));

    await waitFor(() => {
      expect(mockClockOut).toHaveBeenCalledWith(
        "emp-001",
        "2026-02-25T09:00:00.000Z"
      );
    });

    await waitFor(() => {
      expect(mockToast.success).toHaveBeenCalledWith("退勤を記録しました");
    });
  });

  // ========================================
  // 7. API エラー時にエラートーストが表示される
  // ========================================
  it("API エラー時にエラートーストが表示される", async () => {
    const user = userEvent.setup();
    mockFetchTodayAttendance.mockResolvedValue(null);
    mockClockIn.mockRejectedValue(new Error("Network Error"));

    renderModal();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /出勤/ })).toBeEnabled();
    });

    await user.click(screen.getByRole("button", { name: /出勤/ }));

    // apiError が呼ばれたことを確認する
    await waitFor(() => {
      expect(mockToast.apiError).toHaveBeenCalled();
    });
  });

  // ========================================
  // 8. モーダル非表示時はレンダリングしない
  // ========================================
  it("open=false のときモーダルコンテンツが表示されない", () => {
    mockFetchTodayAttendance.mockResolvedValue(null);
    renderModal({ open: false });

    // モーダル内のボタンが存在しないことを確認する
    expect(screen.queryByRole("button", { name: /出勤/ })).not.toBeInTheDocument();
  });
});
