package com.example.kintai.attendance.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 日次勤怠一覧レスポンスDTO — GET /api/v1/attendances/daily の返却値
 *
 * <p>日次勤怠一覧画面（SCR-ATT-002）で使用する。
 * ページネーション情報付きで日次サマリーを返却する。</p>
 *
 * @param content       現在ページの日次サマリー一覧
 * @param page          ページ番号（0始まり）
 * @param size          1ページあたりの件数
 * @param totalElements 全件数
 * @param totalPages    総ページ数
 */
public record DailyAttendancePageResponse(
        List<DailyAttendanceRow> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /**
     * 日次勤怠サマリー行 — テーブルの1行に対応する
     *
     * @param attendanceId         勤怠記録ID
     * @param employeeId           従業員ID
     * @param workDate             勤務日（YYYY-MM-DD）
     * @param status               勤怠ステータス
     * @param clockInTime          出勤時刻（UTC Instant）
     * @param clockOutTime         退勤時刻（UTC Instant、未退勤はnull）
     * @param scheduledMinutes     所定勤務時間（分）
     * @param actualMinutes        実労働時間（分）
     * @param breakMinutes         休憩時間（分）
     * @param netWorkMinutes       正味勤務時間（分）
     * @param regularOvertimeMinutes 時間外残業（分）
     * @param lateNightMinutes     深夜残業（分）
     * @param holidayMinutes       休日勤務（分）
     * @param totalOvertimeMinutes 合計残業時間（分）
     * @param updatedAt            更新日時（UTC Instant）
     */
    public record DailyAttendanceRow(
            UUID attendanceId,
            String employeeId,
            LocalDate workDate,
            String status,
            Instant clockInTime,
            Instant clockOutTime,
            int scheduledMinutes,
            int actualMinutes,
            int breakMinutes,
            int netWorkMinutes,
            int regularOvertimeMinutes,
            int lateNightMinutes,
            int holidayMinutes,
            int totalOvertimeMinutes,
            Instant updatedAt
    ) {}
}
