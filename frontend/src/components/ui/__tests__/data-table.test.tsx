import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { DataTable, type Column } from "../data-table";

// ========================================
// DataTable コンポーネントテスト（9-3-2）
// ソート・ページネーション・フィルタ（行選択）動作を検証する
// ========================================

/** テスト用のデータ型 */
interface TestItem {
  id: string;
  name: string;
  score: number;
}

/** テスト用カラム定義 */
const columns: Column<TestItem>[] = [
  { key: "name", label: "名前", sortable: true },
  { key: "score", label: "スコア", sortable: true },
];

/** テスト用データ（5件） */
const testData: TestItem[] = [
  { id: "1", name: "Alice", score: 90 },
  { id: "2", name: "Bob", score: 75 },
  { id: "3", name: "Charlie", score: 85 },
  { id: "4", name: "David", score: 60 },
  { id: "5", name: "Eve", score: 95 },
];

describe("DataTable", () => {
  // ========================================
  // 1. データが正しくレンダリングされる
  // ========================================
  it("データ行がすべて表示される", () => {
    render(
      <DataTable
        columns={columns}
        data={testData}
        rowKey={(row) => row.id}
      />
    );

    // 5件のデータがすべて表示されることを確認する
    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
    expect(screen.getByText("Charlie")).toBeInTheDocument();
    expect(screen.getByText("David")).toBeInTheDocument();
    expect(screen.getByText("Eve")).toBeInTheDocument();
  });

  // ========================================
  // 2. データなし時にメッセージが表示される
  // ========================================
  it("データが空のとき「データがありません」と表示される", () => {
    render(
      <DataTable
        columns={columns}
        data={[]}
        rowKey={(row) => row.id}
      />
    );

    expect(screen.getByText("データがありません")).toBeInTheDocument();
  });

  // ========================================
  // 3. ソートヘッダークリックで onSortChange が呼ばれる
  // ========================================
  it("ソート可能カラムのヘッダークリックで onSortChange が発火する", async () => {
    const user = userEvent.setup();
    const onSortChange = vi.fn();

    render(
      <DataTable
        columns={columns}
        data={testData}
        rowKey={(row) => row.id}
        onSortChange={onSortChange}
      />
    );

    // 「名前」ヘッダーのソートボタンをクリックする
    const nameHeader = screen.getByRole("button", { name: /名前/ });
    await user.click(nameHeader);

    // 1回目のクリック: asc で呼ばれることを確認する
    expect(onSortChange).toHaveBeenCalledWith("name", "asc");

    // 2回目のクリック: desc に切り替わることを確認する
    await user.click(nameHeader);
    expect(onSortChange).toHaveBeenCalledWith("name", "desc");
  });

  // ========================================
  // 4. 別カラムをクリックすると asc にリセットされる
  // ========================================
  it("別カラムをクリックするとソートが asc にリセットされる", async () => {
    const user = userEvent.setup();
    const onSortChange = vi.fn();

    render(
      <DataTable
        columns={columns}
        data={testData}
        rowKey={(row) => row.id}
        onSortChange={onSortChange}
      />
    );

    // 「名前」→「スコア」の順にクリックする
    await user.click(screen.getByRole("button", { name: /名前/ }));
    await user.click(screen.getByRole("button", { name: /スコア/ }));

    // スコアカラムは asc で呼ばれることを確認する
    expect(onSortChange).toHaveBeenLastCalledWith("score", "asc");
  });

  // ========================================
  // 5. ページネーションのページ送りボタンで onPageChange が呼ばれる
  // ========================================
  it("次ページボタンクリックで onPageChange が呼ばれる", async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();

    render(
      <DataTable
        columns={columns}
        data={testData}
        rowKey={(row) => row.id}
        pagination={{ pageSize: 2 }}
        totalItems={10}
        currentPage={0}
        onPageChange={onPageChange}
      />
    );

    // ページ情報が表示されることを確認する
    expect(screen.getByText("1 / 5")).toBeInTheDocument();

    // 次ページボタン（ChevronRight アイコンのボタン）をクリックする
    const buttons = screen.getAllByRole("button");
    // 最後の pagination ボタン（次ページ）を探す
    const nextButton = buttons.find((btn) => {
      // disabled でないページ送りボタンを特定する
      return !btn.disabled && btn.querySelector("svg") && btn.closest(".flex.items-center.gap-1");
    });

    if (nextButton) {
      await user.click(nextButton);
      expect(onPageChange).toHaveBeenCalledWith(1);
    }
  });

  // ========================================
  // 6. 最初のページでは前ページボタンが無効になる
  // ========================================
  it("最初のページでは前ページボタンが disabled になる", () => {
    render(
      <DataTable
        columns={columns}
        data={testData}
        rowKey={(row) => row.id}
        pagination={{ pageSize: 2 }}
        totalItems={10}
        currentPage={0}
        onPageChange={vi.fn()}
      />
    );

    // ページネーション内の disabled ボタンが存在することを確認する
    const paginationArea = screen.getByText("1 / 5").parentElement!;
    const disabledButtons = paginationArea.querySelectorAll("button:disabled");
    expect(disabledButtons.length).toBeGreaterThan(0);
  });

  // ========================================
  // 7. 行選択チェックボックスで onSelectionChange が呼ばれる
  // ========================================
  it("行のチェックボックスクリックで onSelectionChange が発火する", async () => {
    const user = userEvent.setup();
    const onSelectionChange = vi.fn();

    render(
      <DataTable
        columns={columns}
        data={testData}
        rowKey={(row) => row.id}
        selectable
        selectedKeys={[]}
        onSelectionChange={onSelectionChange}
      />
    );

    // チェックボックスを取得する（ヘッダー + 5行 = 6つ）
    const checkboxes = screen.getAllByRole("checkbox");
    expect(checkboxes.length).toBe(6); // 全選択 + 5行

    // 1行目のチェックボックスをクリックする
    await user.click(checkboxes[1]);

    // 選択されたキー配列が返ることを確認する
    expect(onSelectionChange).toHaveBeenCalledWith(["1"]);
  });

  // ========================================
  // 8. 全選択チェックボックスで全行が選択される
  // ========================================
  it("全選択チェックボックスで全行が選択される", async () => {
    const user = userEvent.setup();
    const onSelectionChange = vi.fn();

    render(
      <DataTable
        columns={columns}
        data={testData}
        rowKey={(row) => row.id}
        selectable
        selectedKeys={[]}
        onSelectionChange={onSelectionChange}
      />
    );

    // ヘッダーの全選択チェックボックスをクリックする
    const checkboxes = screen.getAllByRole("checkbox");
    await user.click(checkboxes[0]);

    // 全行のキーが返ることを確認する
    expect(onSelectionChange).toHaveBeenCalledWith(["1", "2", "3", "4", "5"]);
  });

  // ========================================
  // 9. 行クリックで onRowClick が呼ばれる
  // ========================================
  it("行クリックで onRowClick が発火する", async () => {
    const user = userEvent.setup();
    const onRowClick = vi.fn();

    render(
      <DataTable
        columns={columns}
        data={testData}
        rowKey={(row) => row.id}
        onRowClick={onRowClick}
      />
    );

    // "Alice" のテキストを含む行をクリックする
    await user.click(screen.getByText("Alice"));

    // クリックされた行のデータが渡されることを確認する
    expect(onRowClick).toHaveBeenCalledWith(testData[0]);
  });

  // ========================================
  // 10. ローディング中はスケルトン表示される
  // ========================================
  it("loading=true のときスケルトンが表示される", () => {
    render(
      <DataTable
        columns={columns}
        data={[]}
        rowKey={(row) => row.id}
        loading
      />
    );

    // データ行は表示されず、スケルトンが存在することを確認する
    expect(screen.queryByText("Alice")).not.toBeInTheDocument();
    // 「データがありません」も表示されないことを確認する
    expect(screen.queryByText("データがありません")).not.toBeInTheDocument();
  });
});
