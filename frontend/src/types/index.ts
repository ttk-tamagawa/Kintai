// ========================================
// 共通型定義
// ========================================

/** ユーザーロール（4段階の権限レベル） */
export type Role = "EMPLOYEE" | "MANAGER" | "HR" | "ADMIN";

/** 勤務形態（固定勤務 / シフト勤務 / フレックス勤務） */
export type EmploymentType = "FULL_TIME" | "SHIFT" | "FLEX";

/** 勤怠ステータス（状態遷移: NOT_CLOCKED → CLOCKED_IN → CLOCKED_OUT → FINALIZED） */
export type AttendanceStatus =
  | "NOT_CLOCKED"
  | "CLOCKED_IN"
  | "CLOCKED_OUT"
  | "FINALIZED";

/** 打刻元（打刻がどこから行われたか） */
export type ClockSource = "WEB" | "MOBILE" | "MANUAL" | "CORRECTION";

/** シフトスケジュールステータス */
export type ScheduleStatus = "DRAFT" | "PUBLISHED";

// ========================================
// 認証関連
// ========================================

/** ログインユーザー情報 */
export interface AuthUser {
  employeeId: string;
  name: string;
  email: string;
  departmentId: string;
  departmentName: string;
  roles: Role[];
}

// ========================================
// 勤怠記録 API レスポンス型
// ========================================

/** 出勤レスポンス */
export interface ClockInResponse {
  attendanceId: string;
  employeeId: string;
  workDate: string;
  status: AttendanceStatus;
  clockIn: string;
  source: ClockSource;
  updatedAt: string;
}

/** 退勤レスポンス */
export interface ClockOutResponse {
  attendanceId: string;
  employeeId: string;
  workDate: string;
  status: AttendanceStatus;
  clockIn: string;
  clockOut: string;
  breakMinutes: number;
  netWorkMinutes: number;
  overtimeMinutes: number;
  source: ClockSource;
  updatedAt: string;
}

/** 休憩開始レスポンス */
export interface BreakStartResponse {
  attendanceId: string;
  employeeId: string;
  workDate: string;
  status: AttendanceStatus;
  onBreak: boolean;
  currentBreakStart: string;
  updatedAt: string;
}

/** 休憩終了レスポンス */
export interface BreakEndResponse {
  attendanceId: string;
  employeeId: string;
  workDate: string;
  status: AttendanceStatus;
  onBreak: boolean;
  breakMinutes: number;
  updatedAt: string;
}

/** 本日の勤怠情報 */
export interface TodayAttendance {
  attendanceId: string | null;
  employeeId: string;
  workDate: string;
  status: AttendanceStatus;
  clockIn: string | null;
  clockOut: string | null;
  breakMinutes: number;
  netWorkMinutes: number;
  overtimeMinutes: number;
  onBreak: boolean;
}

/** 日次勤怠一覧のアイテム */
export interface DailyAttendanceItem {
  attendanceId: string;
  employeeId: string;
  employeeName: string;
  workDate: string;
  clockIn: string | null;
  clockOut: string | null;
  breakMinutes: number;
  netWorkMinutes: number;
  overtimeMinutes: number;
  status: AttendanceStatus;
}

/** 月次サマリーのKPI（APIレスポンスのフィールド名に準拠） */
export interface MonthlySummaryKpi {
  totalWorkDays: number;
  avgWorkDays: number;
  totalWorkHours: number;
  avgWorkHours: number;
  totalOvertimeHours: number;
  avgOvertimeHours: number;
  totalPaidLeaveUsed: number;
}

/** 月次サマリーの従業員行 */
export interface MonthlyEmployeeSummary {
  employeeId: string;
  employeeName: string;
  workDays: number;
  totalWorkHours: number;
  totalOvertimeHours: number;
  lateNightHours: number;
  paidLeaveUsed: number;
}

/** 月次サマリーAPIのフルレスポンス */
export interface MonthlySummaryResponse {
  kpi: MonthlySummaryKpi;
  content: MonthlyEmployeeSummary[];
  page: PageInfo;
}

/** 部門ダッシュボードのKPI（APIレスポンスのフィールド名に準拠） */
export interface DepartmentDashboardKpi {
  avgOvertimeHours: number;
  totalOvertimeAlertCount: number;
  totalMissingClockCount: number;
  avgAttendanceRate: number;
}

/** 部門別テーブルのアイテム */
export interface DepartmentDashboardItem {
  departmentId: string;
  departmentName: string;
  headCount: number;
  avgOvertimeHours: number;
  maxOvertimeHours: number;
  overtimeAlertCount: number;
  missingClockCount: number;
  attendanceRate: number;
}

/** 部門ダッシュボードAPIのフルレスポンス */
export interface DepartmentDashboardResponse {
  kpi: DepartmentDashboardKpi;
  previousMonth: DepartmentDashboardKpi;
  content: DepartmentDashboardItem[];
  page: PageInfo;
}

/** ページ情報（APIレスポンス共通） */
export interface PageInfo {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** 日次勤怠一覧APIのフルレスポンス */
export interface DailyAttendanceResponse {
  content: DailyAttendanceItem[];
  page: PageInfo;
}

/** ページネーションレスポンス（汎用） */
export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

// ========================================
// シフト API レスポンス型
// ========================================

/** シフトパターン */
export interface ShiftPattern {
  id: string;
  name: string;
  startTime: string;
  endTime: string;
  breakMinutes: number;
  isOvernight: boolean;
  isActive: boolean;
  createdAt: string;
}

/** 週次スケジュール */
export interface WeeklySchedule {
  id: string;
  employeeId: string;
  employeeName: string;
  weekStartDate: string;
  status: ScheduleStatus;
  monday: ShiftAssignment | null;
  tuesday: ShiftAssignment | null;
  wednesday: ShiftAssignment | null;
  thursday: ShiftAssignment | null;
  friday: ShiftAssignment | null;
  saturday: ShiftAssignment | null;
  sunday: ShiftAssignment | null;
}

/** シフト割当（1日分） */
export interface ShiftAssignment {
  patternId: string;
  patternName: string;
  startTime: string;
  endTime: string;
}

// ========================================
// 共通マスタ型
// ========================================

/** 従業員 */
export interface Employee {
  id: string;
  name: string;
  email: string;
  departmentId: string;
  role: Role;
  workType: EmploymentType;
}

/** 部署 */
export interface Department {
  id: string;
  name: string;
  managerId: string | null;
}
