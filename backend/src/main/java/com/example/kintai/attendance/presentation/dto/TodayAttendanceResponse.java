package com.example.kintai.attendance.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 当日勤怠ステータスレスポンスDTO — GET /api/v1/attendances/today の返却値
 *
 * <p>勤怠打刻モーダルの初期表示データとして使用する。
 * 未退勤の場合は netWorkMinutes / totalOvertimeMinutes が null になる。</p>
 *
 * @param attendanceId       勤怠記録ID
 * @param employeeId         従業員ID
 * @param workDate           勤務日（YYYY-MM-DD）
 * @param status             勤怠ステータス（NOT_CLOCKED / CLOCKED_IN / CLOCKED_OUT / FINALIZED）
 * @param onBreak            休憩中フラグ（CLOCKED_IN かつ未終了の休憩がある場合true）
 * @param clockIn            出勤時刻（UTC Instant）
 * @param clockOut           退勤時刻（UTC Instant、未退勤の場合はnull）
 * @param breakMinutes       合計休憩時間（分）
 * @param netWorkMinutes     正味勤務時間（分、未計算の場合はnull）
 * @param totalOvertimeMinutes 合計残業時間（分、未計算の場合はnull）
 * @param updatedAt          更新日時（UTC Instant）
 */
public record TodayAttendanceResponse(
        UUID attendanceId,
        String employeeId,
        LocalDate workDate,
        String status,
        boolean onBreak,
        Instant clockIn,
        Instant clockOut,
        int breakMinutes,
        Integer netWorkMinutes,
        Integer totalOvertimeMinutes,
        Instant updatedAt
) {}
