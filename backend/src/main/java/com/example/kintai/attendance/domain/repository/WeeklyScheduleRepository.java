package com.example.kintai.attendance.domain.repository;

import com.example.kintai.attendance.domain.model.shift.WeeklySchedule;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 週次スケジュールリポジトリ — 週次スケジュール集約の永続化インターフェース
 *
 * <p>従業員+週の組み合わせでユニーク（1従業員1週1スケジュール）。
 * 楽観的ロックによるバージョン管理を行う。</p>
 */
public interface WeeklyScheduleRepository {

    /**
     * スケジュールIDで検索する
     */
    Optional<WeeklySchedule> findById(ScheduleId id);

    /**
     * 従業員IDと週開始日で検索する
     */
    Optional<WeeklySchedule> findByEmployeeIdAndWeekStartDate(EmployeeId employeeId, LocalDate weekStartDate);

    /**
     * 週次スケジュールを保存する（新規作成または更新）
     *
     * @param schedule 保存する週次スケジュール
     * @return バージョン更新済みの週次スケジュール
     */
    WeeklySchedule save(WeeklySchedule schedule);

    /**
     * 従業員IDと週開始日の組み合わせが既に存在するか確認する
     */
    boolean existsByEmployeeIdAndWeekStartDate(EmployeeId employeeId, LocalDate weekStartDate);
}
