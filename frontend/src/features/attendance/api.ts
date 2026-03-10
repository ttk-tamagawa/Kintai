import api from "@/lib/api";
import type {
  TodayAttendance,
  ClockInResponse,
  ClockOutResponse,
  BreakStartResponse,
  BreakEndResponse,
  DailyAttendanceResponse,
  DailyAttendanceItem,
  MonthlySummaryResponse,
  DepartmentDashboardResponse,
  PageInfo,
} from "@/types";

// ========================================
// 勤怠記録 API クライアント
// 全ての勤怠関連APIエンドポイントの呼び出し関数
// ========================================

// --- ユーティリティ ---

/** "YYYY-MM" 形式を { year, month } に分割する */
function parseYearMonth(ym: string): { year: number; month: number } {
  const [y, m] = ym.split("-").map(Number);
  return { year: y, month: m };
}

/** バックエンドのフラットなページネーションを PageInfo に変換する */
function toPageInfo(raw: {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}): PageInfo {
  return {
    number: raw.page,
    size: raw.size,
    totalElements: raw.totalElements,
    totalPages: raw.totalPages,
  };
}

// --- 打刻系 API ---

/** 本日の勤怠ステータスを取得する（employeeId をクエリパラメータとして送信） */
export async function fetchTodayAttendance(
  employeeId: string
): Promise<TodayAttendance | null> {
  const response = await api.get("/attendances/today", {
    params: { employeeId },
    // 204 No Content の場合は null を返す
    validateStatus: (status: number) => status === 200 || status === 204,
  });
  // 204 No Content またはデータなしの場合
  if (response.status === 204 || !response.data || !response.data.status) {
    return null;
  }
  return response.data as TodayAttendance;
}

/** 出勤打刻を実行する */
export async function clockIn(
  employeeId: string,
  clockTime: string
): Promise<ClockInResponse> {
  const { data } = await api.post<ClockInResponse>("/attendances/clock-in", {
    employeeId,
    clockTime,
    source: "WEB",
  });
  return data;
}

/** 退勤打刻を実行する */
export async function clockOut(
  employeeId: string,
  clockTime: string
): Promise<ClockOutResponse> {
  const { data } = await api.post<ClockOutResponse>("/attendances/clock-out", {
    employeeId,
    clockTime,
    source: "WEB",
  });
  return data;
}

/** 休憩開始を記録する */
export async function breakStart(
  employeeId: string,
  clockTime: string
): Promise<BreakStartResponse> {
  const { data } = await api.post<BreakStartResponse>(
    "/attendances/break-start",
    { employeeId, clockTime, source: "WEB" }
  );
  return data;
}

/** 休憩終了を記録する */
export async function breakEnd(
  employeeId: string,
  clockTime: string
): Promise<BreakEndResponse> {
  const { data } = await api.post<BreakEndResponse>(
    "/attendances/break-end",
    { employeeId, clockTime, source: "WEB" }
  );
  return data;
}

// --- クエリ系 API ---

/** 日次勤怠一覧のクエリパラメータ */
export interface DailyAttendanceParams {
  employeeId?: string;
  dateFrom?: string;
  dateTo?: string;
  status?: string;
  page?: number;
  size?: number;
  sortField?: string;
  sortDirection?: string;
}

/** 日次勤怠一覧を取得する（レスポンスのフラットなページネーションを PageInfo に変換） */
export async function fetchDailyAttendances(
  params: DailyAttendanceParams
): Promise<{ content: DailyAttendanceItem[]; page: PageInfo }> {
  const { data } = await api.get<DailyAttendanceResponse>(
    "/attendances/daily",
    { params }
  );
  // バックエンドのフラットな構造 → フロントエンド用に変換
  return {
    content: data.content,
    page: toPageInfo(data),
  };
}

/** 月次サマリーのクエリパラメータ */
export interface MonthlySummaryParams {
  departmentId?: string;
  month?: string;
  page?: number;
  size?: number;
  sortField?: string;
  sortDirection?: string;
}

/** 月次勤怠サマリーを取得する（month を year/month に分割、レスポンス構造を変換） */
export async function fetchMonthlySummary(
  params: MonthlySummaryParams
): Promise<{
  kpi: MonthlySummaryResponse["kpi"];
  content: MonthlySummaryResponse["employees"]["content"];
  page: PageInfo;
}> {
  // month "YYYY-MM" → year, month に分割する
  const yearMonth = params.month ? parseYearMonth(params.month) : {};
  const { data } = await api.get<MonthlySummaryResponse>(
    "/attendances/monthly-summary",
    {
      params: {
        departmentId: params.departmentId,
        ...yearMonth,
        page: params.page,
        size: params.size,
        sortField: params.sortField,
        sortDirection: params.sortDirection,
      },
    }
  );
  // バックエンドの { kpi, employees: { content, page, ... } } → フロントエンド用に変換
  return {
    kpi: data.kpi,
    content: data.employees.content,
    page: toPageInfo(data.employees),
  };
}

/** 月次サマリーCSVをダウンロードする（month を year/month に分割） */
export async function exportMonthlySummary(
  departmentId?: string,
  month?: string
): Promise<void> {
  const yearMonth = month ? parseYearMonth(month) : {};
  const response = await api.get("/attendances/monthly-summary/export", {
    params: { departmentId, ...yearMonth },
    responseType: "blob",
  });
  downloadBlob(response.data, `monthly-summary_${month ?? "all"}.csv`);
}

/** 部門ダッシュボードのクエリパラメータ */
export interface DepartmentDashboardParams {
  departmentId?: string;
  month?: string;
  page?: number;
  size?: number;
  sortField?: string;
  sortDirection?: string;
}

/** 部門別勤怠ダッシュボードを取得する（month を year/month に分割、レスポンス構造を変換） */
export async function fetchDepartmentDashboard(
  params: DepartmentDashboardParams
): Promise<{
  kpi: DepartmentDashboardResponse["kpi"];
  previousMonth: DepartmentDashboardResponse["previousMonth"];
  content: DepartmentDashboardResponse["departments"]["content"];
  page: PageInfo;
}> {
  const yearMonth = params.month ? parseYearMonth(params.month) : {};
  const { data } = await api.get<DepartmentDashboardResponse>(
    "/attendances/department-dashboard",
    {
      params: {
        departmentId: params.departmentId,
        ...yearMonth,
        page: params.page,
        size: params.size,
        sortField: params.sortField,
        sortDirection: params.sortDirection,
      },
    }
  );
  // バックエンドの { kpi, previousMonth, departments: { content, page, ... } } → フロントエンド用に変換
  return {
    kpi: data.kpi,
    previousMonth: data.previousMonth,
    content: data.departments.content,
    page: toPageInfo(data.departments),
  };
}

/** 部門ダッシュボードCSVをダウンロードする（month を year/month に分割） */
export async function exportDepartmentDashboard(
  departmentId?: string,
  month?: string
): Promise<void> {
  const yearMonth = month ? parseYearMonth(month) : {};
  const response = await api.get("/attendances/department-dashboard/export", {
    params: { departmentId, ...yearMonth },
    responseType: "blob",
  });
  downloadBlob(response.data, `department-dashboard_${month ?? "all"}.csv`);
}

// --- ユーティリティ ---

/** Blobデータをファイルとしてダウンロードする */
function downloadBlob(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}
