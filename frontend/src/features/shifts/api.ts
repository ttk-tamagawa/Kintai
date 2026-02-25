import api from "@/lib/api";
import type {
  ShiftPattern,
  ShiftPatternsResponse,
  ScheduleItem,
  ShiftSchedulesResponse,
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
  page?: number;
  size?: number;
  sort?: string;
}

/** シフトパターン一覧を取得する */
export async function fetchPatterns(
  params: PatternListParams
): Promise<ShiftPatternsResponse> {
  const { data } = await api.get<ShiftPatternsResponse>("/shifts/patterns", {
    params,
  });
  return data;
}

/** 有効なシフトパターンのみ取得する（割当セレクト用） */
export async function fetchActivePatterns(): Promise<ShiftPattern[]> {
  const { data } = await api.get<ShiftPatternsResponse>("/shifts/patterns", {
    params: { isActive: true, size: 100 },
  });
  return data.content;
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
  status?: string;
  page?: number;
  size?: number;
  sort?: string;
}

/** スケジュール一覧（カレンダー）を取得する */
export async function fetchSchedules(
  params: ScheduleListParams
): Promise<ShiftSchedulesResponse> {
  const { data } = await api.get<ShiftSchedulesResponse>(
    "/shifts/schedules",
    { params }
  );
  return data;
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
  const { data } = await api.post<ScheduleItem>("/shifts/schedules", req);
  return data;
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
  const { data } = await api.put<ScheduleItem>(
    `/shifts/schedules/${scheduleId}`,
    req
  );
  return data;
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

/** 従業員一覧を取得する（部門配下の従業員） */
export async function fetchEmployees(): Promise<EmployeeOption[]> {
  const { data } = await api.get<{ content: EmployeeOption[] }>(
    "/employees",
    { params: { size: 100 } }
  );
  return data.content;
}
