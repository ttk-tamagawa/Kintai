import api from "@/lib/api";
import type {
  TodayAttendance,
  ClockInResponse,
  ClockOutResponse,
  BreakStartResponse,
  BreakEndResponse,
  DailyAttendanceResponse,
  MonthlySummaryResponse,
  DepartmentDashboardResponse,
} from "@/types";

// ========================================
// 勤怠記録 API クライアント
// 全ての勤怠関連APIエンドポイントの呼び出し関数
// ========================================

// --- 打刻系 API ---

/** 本日の勤怠ステータスを取得する */
export async function fetchTodayAttendance(): Promise<TodayAttendance | null> {
  const { data } = await api.get("/attendances/today");
  // レコードが存在しない場合、空オブジェクト {} が返る
  if (!data || !data.status) return null;
  return data as TodayAttendance;
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
  sort?: string;
}

/** 日次勤怠一覧を取得する */
export async function fetchDailyAttendances(
  params: DailyAttendanceParams
): Promise<DailyAttendanceResponse> {
  const { data } = await api.get<DailyAttendanceResponse>(
    "/attendances/daily",
    { params }
  );
  return data;
}

/** 月次サマリーのクエリパラメータ */
export interface MonthlySummaryParams {
  departmentId?: string;
  month?: string;
  page?: number;
  size?: number;
  sort?: string;
}

/** 月次勤怠サマリーを取得する */
export async function fetchMonthlySummary(
  params: MonthlySummaryParams
): Promise<MonthlySummaryResponse> {
  const { data } = await api.get<MonthlySummaryResponse>(
    "/attendances/monthly-summary",
    { params }
  );
  return data;
}

/** 月次サマリーCSVをダウンロードする */
export async function exportMonthlySummary(
  departmentId?: string,
  month?: string
): Promise<void> {
  const response = await api.get("/attendances/monthly-summary/export", {
    params: { departmentId, month },
    responseType: "blob",
  });
  // Blobからダウンロードリンクを生成する
  downloadBlob(response.data, `monthly-summary_${month ?? "all"}.csv`);
}

/** 部門ダッシュボードのクエリパラメータ */
export interface DepartmentDashboardParams {
  departmentId?: string;
  month?: string;
  page?: number;
  size?: number;
  sort?: string;
}

/** 部門別勤怠ダッシュボードを取得する */
export async function fetchDepartmentDashboard(
  params: DepartmentDashboardParams
): Promise<DepartmentDashboardResponse> {
  const { data } = await api.get<DepartmentDashboardResponse>(
    "/attendances/department-dashboard",
    { params }
  );
  return data;
}

/** 部門ダッシュボードCSVをダウンロードする */
export async function exportDepartmentDashboard(
  departmentId?: string,
  month?: string
): Promise<void> {
  const response = await api.get("/attendances/department-dashboard/export", {
    params: { departmentId, month },
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
