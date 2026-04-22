package com.example.kintai.attendance.application.query;

import com.example.kintai.shared.domain.model.EmployeeId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * シフトファインダー — Read Model 参照用インターフェース
 *
 * <p>CQRS の読み取り側を担当する。
 * シフトパターン一覧とカレンダービュー（週次スケジュール）のデータを提供する。
 * weekly_schedule_summaries テーブルからパターン名つきのデータを取得する。</p>
 *
 * <p>アプリケーション層に配置することで、ドメイン層を書き込み側の責務に限定する。</p>
 */
public interface ShiftFinder {

    // ========================
    // Read Model DTO定義
    // ========================

    /**
     * シフトパターン概要 — shift_patternsテーブルの読み取り結果
     */
    record PatternSummary(
            UUID id,
            String name,
            String startTime,
            String endTime,
            int breakMinutes,
            boolean isOvernight,
            boolean isActive,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * 週次スケジュール概要 — weekly_schedule_summariesテーブルの読み取り結果
     *
     * <p>各曜日のパターンIDとパターン名を保持する。
     * パターン名はRead Modelに非正規化されているため、JOINなしで取得できる。
     * employeeNameはemployeesテーブルをJOINして取得する。</p>
     */
    record ScheduleSummary(
            UUID scheduleId,
            String employeeId,
            String employeeName,
            LocalDate weekStartDate,
            String status,
            UUID mondayPatternId, String mondayPatternName,
            UUID tuesdayPatternId, String tuesdayPatternName,
            UUID wednesdayPatternId, String wednesdayPatternName,
            UUID thursdayPatternId, String thursdayPatternName,
            UUID fridayPatternId, String fridayPatternName,
            UUID saturdayPatternId, String saturdayPatternName,
            UUID sundayPatternId, String sundayPatternName,
            int assignedDays,
            Instant createdAt,
            Instant updatedAt
    ) {}

    // ========================
    // パターンクエリ
    // ========================

    /**
     * パターン一覧を取得する（有効/無効フィルタ対応）
     *
     * @param isActive true=有効のみ, false=無効のみ, null=全件
     */
    List<PatternSummary> findPatterns(Boolean isActive);

    /**
     * パターンIDで概要を取得する
     */
    Optional<PatternSummary> findPatternById(UUID patternId);

    // ========================
    // カレンダービュークエリ
    // ========================

    /**
     * 従業員と期間で週次スケジュール一覧を取得する
     */
    List<ScheduleSummary> findSchedules(EmployeeId employeeId, LocalDate from, LocalDate to);

    /**
     * 期間のみで全従業員の週次スケジュール一覧を取得する（カレンダー表示用）
     */
    List<ScheduleSummary> findAllSchedules(LocalDate from, LocalDate to);

    /**
     * スケジュールIDで概要を取得する
     */
    Optional<ScheduleSummary> findScheduleById(UUID scheduleId);
}
