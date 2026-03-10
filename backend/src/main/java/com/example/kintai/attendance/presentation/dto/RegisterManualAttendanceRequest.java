package com.example.kintai.attendance.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 手動勤務登録リクエストDTO — POST /api/v1/internal/attendances/register のリクエストボディ
 *
 * <p>承認済みの手動勤務登録申請に基づき、勤務実績を登録する。
 * 打刻漏れや出張等で出退勤記録がない場合に使用する。</p>
 *
 * @param employeeId     従業員ID（必須）
 * @param workDate       勤務日（必須、YYYY-MM-DD）
 * @param startTime      勤務開始時刻（必須、ISO 8601形式）
 * @param endTime        勤務終了時刻（必須、ISO 8601形式）
 * @param type           勤務種別（必須）— "通常勤務" / "出張" 等
 * @param reason         登録理由（必須）— "打刻漏れのため" 等
 * @param approvalId     承認ID（必須）
 * @param shiftPatternId シフトパターンID（任意、シフト制の場合に指定）
 */
public record RegisterManualAttendanceRequest(
        @NotNull String employeeId,
        @NotNull LocalDate workDate,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        @NotNull String type,
        @NotNull String reason,
        @NotNull UUID approvalId,
        UUID shiftPatternId
) {}
