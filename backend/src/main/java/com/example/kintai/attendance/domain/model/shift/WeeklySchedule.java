package com.example.kintai.attendance.domain.model.shift;

import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 週次スケジュール — 集約ルート
 *
 * <p>従業員の1週間分のシフト割当を管理する。
 * 月曜〜日曜の各曜日に対してシフトパターンを割り当てる。
 * 割当がない曜日は「休み」として扱う。</p>
 *
 * <p>不変条件:
 * <ul>
 *   <li>INV-SH-002: 割り当てるパターンはACTIVEであること</li>
 *   <li>INV-SH-003: 1日につきパターンは最大1つ</li>
 *   <li>週の開始日は必ず月曜日であること</li>
 *   <li>最低1日はパターンが割り当てられていること</li>
 * </ul>
 * </p>
 *
 * <p>状態遷移:
 * <ul>
 *   <li>作成 → DRAFT</li>
 *   <li>DRAFT → PUBLISHED（公開）</li>
 *   <li>PUBLISHED → DRAFT（変更時にDRAFTへ戻る）</li>
 * </ul>
 * </p>
 */
public class WeeklySchedule {

    /** スケジュールID */
    private final ScheduleId id;

    /** 従業員ID */
    private final EmployeeId employeeId;

    /** 週の開始日（月曜日） */
    private final LocalDate weekStartDate;

    /** スケジュールステータス */
    private ScheduleStatus status;

    /** 曜日ごとのシフトパターン割当（nullの曜日は休み） */
    private final Map<DayOfWeek, ShiftPatternId> assignments;

    /** 楽観的ロック用バージョン */
    private int version;

    /** 作成日時 */
    private final Instant createdAt;

    /** 更新日時 */
    private Instant updatedAt;

    // ========================
    // コンストラクタ（リポジトリ復元用）
    // ========================

    /**
     * パッケージプライベートコンストラクタ — DB復元用
     */
    WeeklySchedule(
            ScheduleId id,
            EmployeeId employeeId,
            LocalDate weekStartDate,
            ScheduleStatus status,
            Map<DayOfWeek, ShiftPatternId> assignments,
            int version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "スケジュールIDはnullにできません");
        this.employeeId = Objects.requireNonNull(employeeId, "従業員IDはnullにできません");
        this.weekStartDate = Objects.requireNonNull(weekStartDate, "週開始日はnullにできません");
        this.status = Objects.requireNonNull(status, "ステータスはnullにできません");
        this.assignments = new EnumMap<>(
                Objects.requireNonNull(assignments, "割当マップはnullにできません")
        );
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "作成日時はnullにできません");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新日時はnullにできません");
    }

    // ========================
    // DB復元用ファクトリメソッド
    // ========================

    /**
     * DBから読み込んだデータで週次スケジュールを復元する — リポジトリ実装専用
     */
    public static WeeklySchedule reconstruct(
            ScheduleId id, EmployeeId employeeId, LocalDate weekStartDate,
            ScheduleStatus status, Map<DayOfWeek, ShiftPatternId> assignments,
            int version, Instant createdAt, Instant updatedAt
    ) {
        return new WeeklySchedule(
                id, employeeId, weekStartDate, status,
                assignments, version, createdAt, updatedAt
        );
    }

    // ========================
    // ファクトリメソッド
    // ========================

    /**
     * 新規週次スケジュールを作成する
     *
     * <p>ガード条件:
     * <ul>
     *   <li>weekStartDateは月曜日であること</li>
     *   <li>assignmentsは1日以上の割当があること</li>
     * </ul>
     * 初期状態はDRAFT。従業員+週の重複チェックはアプリケーション層で行う。</p>
     *
     * @param employeeId    従業員ID
     * @param weekStartDate 週の開始日（月曜日）
     * @param assignments   曜日ごとのシフトパターン割当
     * @return 新規WeeklyScheduleインスタンス
     */
    public static WeeklySchedule assign(
            EmployeeId employeeId,
            LocalDate weekStartDate,
            Map<DayOfWeek, ShiftPatternId> assignments
    ) {
        // 週開始日が月曜日であることを検証する
        if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException(
                    "週の開始日は月曜日である必要があります（指定: " + weekStartDate.getDayOfWeek() + "）"
            );
        }

        // 最低1日はパターンが割り当てられていることを検証する
        if (assignments == null || assignments.isEmpty()) {
            throw new IllegalArgumentException("最低1日はシフトパターンを割り当ててください");
        }

        Instant now = Instant.now();
        return new WeeklySchedule(
                ScheduleId.generate(),
                employeeId,
                weekStartDate,
                ScheduleStatus.DRAFT,
                assignments,
                0,
                now,
                now
        );
    }

    // ========================
    // コマンドメソッド
    // ========================

    /**
     * スケジュールを変更する
     *
     * <p>割当内容を新しいものに差し替える。
     * PUBLISHEDの場合はDRAFTに戻る（再公開が必要）。</p>
     *
     * @param newAssignments 新しい曜日ごとの割当
     * @throws IllegalArgumentException 割当が空の場合
     */
    public void changeAssignments(Map<DayOfWeek, ShiftPatternId> newAssignments) {
        // 最低1日の割当を検証する
        if (newAssignments == null || newAssignments.isEmpty()) {
            throw new IllegalArgumentException("最低1日はシフトパターンを割り当ててください");
        }

        // 既存の割当をクリアして新しい割当に差し替える
        assignments.clear();
        assignments.putAll(newAssignments);

        // PUBLISHEDの場合はDRAFTに戻す（変更したので再公開が必要）
        if (status == ScheduleStatus.PUBLISHED) {
            status = ScheduleStatus.DRAFT;
        }

        updatedAt = Instant.now();
    }

    /**
     * スケジュールを公開する
     *
     * <p>ガード条件: ステータスがDRAFTであること。
     * 公開すると従業員に通知される（通知はアプリケーション層で実行）。</p>
     *
     * @throws IllegalStateException 既にPUBLISHEDの場合
     */
    public void publish() {
        // ガード条件: DRAFTであることを確認
        if (status != ScheduleStatus.DRAFT) {
            throw new IllegalStateException(
                    "スケジュールを公開できません。現在のステータス: " + status + "（DRAFTである必要があります）"
            );
        }

        // ステータスをPUBLISHEDに遷移する
        status = ScheduleStatus.PUBLISHED;
        updatedAt = Instant.now();
    }

    // ========================
    // ゲッター
    // ========================

    public ScheduleId getId() { return id; }
    public EmployeeId getEmployeeId() { return employeeId; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public ScheduleStatus getStatus() { return status; }

    /**
     * 割当マップを取得する（変更不可）
     *
     * <p>外部からの変更を防ぐためunmodifiableで返す。</p>
     */
    public Map<DayOfWeek, ShiftPatternId> getAssignments() {
        return Collections.unmodifiableMap(assignments);
    }

    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
