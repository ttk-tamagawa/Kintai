package com.example.kintai.attendance.presentation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * シフトパターンレスポンスDTO — パターン関連APIの共通返却値
 *
 * <p>以下のエンドポイントで使用する:
 * <ul>
 *   <li>POST /api/v1/shifts/patterns — パターン作成（201）</li>
 *   <li>GET /api/v1/shifts/patterns — パターン一覧（200）</li>
 *   <li>GET /api/v1/shifts/patterns/{id} — パターン詳細（200）</li>
 *   <li>POST /api/v1/shifts/patterns/{id}/actions/deactivate — 無効化（200）</li>
 *   <li>POST /api/v1/shifts/patterns/{id}/actions/reactivate — 再有効化（200）</li>
 * </ul>
 * </p>
 *
 * @param patternId    シフトパターンID
 * @param name         パターン名
 * @param startTime    勤務開始時刻（HH:mm形式の文字列）
 * @param endTime      勤務終了時刻（HH:mm形式の文字列）
 * @param breakMinutes 休憩時間（分）
 * @param isOvernight  夜勤フラグ
 * @param isActive     有効フラグ
 * @param createdAt    作成日時（UTC Instant）
 * @param updatedAt    更新日時（UTC Instant）
 */
public record PatternResponse(
        UUID patternId,
        String name,
        String startTime,
        String endTime,
        int breakMinutes,
        boolean isOvernight,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {}
