import api from "@/lib/api";
import type {
  ShiftPattern,
  ScheduleItem,
  DayOfWeek,
} from "@/types";

// ========================================
// シフト管理 API クライアント
// パターン管理・スケジュール管理の全エンドポイント
// ========================================

// --- シフトパターン API ---

/** シフトパターン一覧のクエリパラメータ */
export interface PatternListParams {
  isActive?: boolean;
}

/** シフトパターン一覧を取得する（バックエンドは配列を直接返す） */
export async function fetchPatterns(
  params: PatternListParams
): Promise<ShiftPattern[]> {
  const { data } = await api.get<ShiftPattern[]>("/shifts/patterns", {
    params: { isActive: params.isActive },
  });
  return data;
}

/** 有効なシフトパターンのみ取得する（割当セレクト用。バックエンドは配列を直接返す） */
export async function fetchActivePatterns(): Promise<ShiftPattern[]> {
  const { data } = await api.get<ShiftPattern[]>("/shifts/patterns", {
    params: { isActive: true },
  });
  return data;
}

/** シフトパターン登録リクエスト */
export interface CreatePatternRequest {
  name: string;
  startTime: string;
  endTime: string;
  breakMinutes: number;
  isOvernight: boolean;
}

/** シフトパターンを新規登録する */
export async function createPattern(
  req: CreatePatternRequest
): Promise<ShiftPattern> {
  const { data } = await api.post<ShiftPattern>("/shifts/patterns", req);
  return data;
}

/** シフトパターンを無効化する */
export async function deactivatePattern(patternId: string): Promise<void> {
  await api.post(`/shifts/patterns/${patternId}/actions/deactivate`);
}

/** シフトパターンを再有効化する */
export async function reactivatePattern(patternId: string): Promise<void> {
  await api.post(`/shifts/patterns/${patternId}/actions/reactivate`);
}

// --- 週次スケジュール API ---

/** スケジュール一覧のクエリパラメータ */
export interface ScheduleListParams {
  employeeId?: string;
  weekFrom?: string;
  weekTo?: string;
}

/** バックエンドの ScheduleResponse の型（DayAssignment に startTime/endTime がない） */
interface BackendScheduleResponse {
  scheduleId: string;
  employeeId: string;
  weekStartDate: string;
  status: string;
  assignments: Record<string, { patternId: string; patternName: string }>;
  assignedDays: number;
  createdAt: string;
  updatedAt: string;
}

/** スケジュール一覧を取得する（バックエンドは配列を直接返す） */
export async function fetchSchedules(
  params: ScheduleListParams
): Promise<ScheduleItem[]> {
  const { data } = await api.get<BackendScheduleResponse[]>(
    "/shifts/schedules",
    {
      params: {
        employeeId: params.employeeId,
        weekFrom: params.weekFrom,
        weekTo: params.weekTo,
      },
    }
  );
  // バックエンドのレスポンスをフロントエンドの ScheduleItem に変換する
  return data.map(toScheduleItem);
}

/** バックエンドの ScheduleResponse → フロントエンドの ScheduleItem に変換する */
function toScheduleItem(raw: BackendScheduleResponse): ScheduleItem {
  const assignments: Partial<Record<DayOfWeek, { patternId: string; patternName: string; startTime: string; endTime: string }>> = {};
  for (const [day, val] of Object.entries(raw.assignments)) {
    assignments[day as DayOfWeek] = {
      patternId: val.patternId,
      patternName: val.patternName,
      // バックエンドの DayAssignment に startTime/endTime がないためデフォルト値を設定
      startTime: "",
      endTime: "",
    };
  }
  return {
    scheduleId: raw.scheduleId,
    employeeId: raw.employeeId,
    // バックエンドに employeeName がないため空文字で代替
    employeeName: "",
    weekStartDate: raw.weekStartDate,
    status: raw.status as ScheduleItem["status"],
    assignments,
  };
}

/** スケジュール新規割当リクエスト */
export interface CreateScheduleRequest {
  employeeId: string;
  weekStartDate: string;
  assignments: Partial<Record<DayOfWeek, string>>;
}

/** シフトスケジュールを新規割当する */
export async function createSchedule(
  req: CreateScheduleRequest
): Promise<ScheduleItem> {
  const { data } = await api.post<BackendScheduleResponse>("/shifts/schedules", req);
  return toScheduleItem(data);
}

/** スケジュール変更リクエスト */
export interface UpdateScheduleRequest {
  assignments: Partial<Record<DayOfWeek, string>>;
}

/** シフトスケジュールを変更する */
export async function updateSchedule(
  scheduleId: string,
  req: UpdateScheduleRequest
): Promise<ScheduleItem> {
  const { data } = await api.put<BackendScheduleResponse>(
    `/shifts/schedules/${scheduleId}`,
    req
  );
  return toScheduleItem(data);
}

/** スケジュールを公開する */
export async function publishSchedule(scheduleId: string): Promise<void> {
  await api.post(`/shifts/schedules/${scheduleId}/actions/publish`);
}

// --- 従業員取得（割当セレクト用） ---

/** 従業員選択肢 */
export interface EmployeeOption {
  employeeId: string;
  employeeName: string;
}

/**
 * 従業員一覧を取得する
 * 注意: バックエンドに /employees エンドポイントが存在しないため、
 * エラーを握り潰して空配列を返す（ShiftAssignModal のフォールバック入力にまかせる）
 */
export async function fetchEmployees(): Promise<EmployeeOption[]> {
  try {
    const { data } = await api.get<{ content: EmployeeOption[] }>(
      "/employees",
      { params: { size: 100 } }
    );
    return data.content;
  } catch {
    // バックエンドに /employees エンドポイントがない場合は空配列を返す
    return [];
  }
}
