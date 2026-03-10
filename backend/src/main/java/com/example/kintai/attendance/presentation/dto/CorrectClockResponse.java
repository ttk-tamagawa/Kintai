package com.example.kintai.attendance.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 打刻修正レスポンスDTO — POST /api/v1/internal/attendances/correct の返却値
 *
 * <p>修正後の勤怠記録の状態を返却する。
 * 退勤済みの場合は勤務時間が再計算されるため、更新後の値を含む。</p>
 *
 * @param attendanceId       勤怠記録ID
 * @param employeeId         従業員ID
 * @param workDate           勤務日（YYYY-MM-DD）
 * @param status             勤怠ステータス
 * @param correctedType      修正した打刻種別
 * @param correctedTime      修正後の打刻時刻（UTC Instant）
 * @param netWorkMinutes     正味勤務時間（分、退勤済みの場合のみ。未退勤はnull）
 * @param totalOvertimeMinutes 合計残業時間（分、退勤済みの場合のみ。未退勤はnull）
 * @param updatedAt          更新日時（UTC Instant）
 */
public record CorrectClockResponse(
        UUID attendanceId,
        String employeeId,
        LocalDate workDate,
        String status,
        String correctedType,
        Instant correctedTime,
        Integer netWorkMinutes,
        Integer totalOvertimeMinutes,
        Instant updatedAt
) {}
