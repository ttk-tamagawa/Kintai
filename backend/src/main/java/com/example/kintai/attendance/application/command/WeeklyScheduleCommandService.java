package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.ScheduleStatus;
import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.model.shift.WeeklySchedule;
import com.example.kintai.attendance.domain.model.shift.event.ShiftAssignedEvent;
import com.example.kintai.attendance.domain.model.shift.event.ShiftChangedEvent;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleEventRepository;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleRepository;
import com.example.kintai.shared.domain.model.EmployeeId;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * 週次スケジュールコマンドサービス — 週次スケジュールの3つの書き込みユースケースを統合するアプリケーションサービス
 *
 * <p>各コマンドメソッドは以下の共通フローで処理する:
 * <ol>
 *   <li>ガード条件を検証する（重複チェック、パターンACTIVE検証等）</li>
 *   <li>ドメインオブジェクトのファクトリ/コマンドメソッドを呼び出す</li>
 *   <li>リポジトリに保存する</li>
 *   <li>ドメインイベントをイベントストアに記録する（JSON形式）</li>
 *   <li>ドメインイベントをSpringイベントとして発行する（プロジェクターがRead Modelを更新）</li>
 * </ol>
 * </p>
 *
 * <p>対応ユースケース:
 * <ul>
 *   <li>UC-SH-004: シフトスケジュールを割り当てる（assignSchedule）</li>
 *   <li>UC-SH-005: シフトスケジュールを変更する（changeSchedule）</li>
 *   <li>UC-SH-006: シフトスケジュールを公開する（publishSchedule）</li>
 * </ul>
 * </p>
 */
@Service
@Transactional
public class WeeklyScheduleCommandService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyScheduleCommandService.class);

    /** 週次スケジュールリポジトリ — スケジュールのCRUD操作 */
    private final WeeklyScheduleRepository weeklyScheduleRepository;

    /** シフトパターンリポジトリ — パターンのACTIVE検証用 */
    private final ShiftPatternRepository shiftPatternRepository;

    /** スケジュールイベントリポジトリ — イベントストアへの追記（INSERT ONLY） */
    private final WeeklyScheduleEventRepository weeklyScheduleEventRepository;

    /** Springイベント発行 — プロジェクターがRead Modelを更新するトリガー */
    private final ApplicationEventPublisher eventPublisher;

    /** JSON変換 — イベントペイロードのシリアライズ用 */
    private final ObjectMapper objectMapper;

    /**
     * コンストラクタ — 5つの依存を注入する
     */
    public WeeklyScheduleCommandService(
            WeeklyScheduleRepository weeklyScheduleRepository,
            ShiftPatternRepository shiftPatternRepository,
            WeeklyScheduleEventRepository weeklyScheduleEventRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.shiftPatternRepository = shiftPatternRepository;
        this.weeklyScheduleEventRepository = weeklyScheduleEventRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    // ========================================
    // UC-SH-004: シフトスケジュールを割り当てる
    // ========================================

    /**
     * 新しい週次スケジュールを割り当てる
     *
     * <p>処理フロー:
     * <ol>
     *   <li>従業員ID+週開始日の重複をチェックする（1従業員1週1スケジュール）</li>
     *   <li>割当パターンが全てACTIVEであることを検証する（INV-SH-002）</li>
     *   <li>WeeklySchedule.assign()でドメインオブジェクトを作成する（DRAFT状態）</li>
     *   <li>リポジトリに保存する</li>
     *   <li>ASSIGNEDイベントをイベントストアに追記する</li>
     *   <li>ShiftAssignedEventを発行する（プロジェクターがRead Modelを更新）</li>
     * </ol>
     * </p>
     *
     * @param employeeId    従業員ID
     * @param weekStartDate 週の開始日（月曜日）
     * @param assignments   曜日ごとのシフトパターン割当（Map<DayOfWeek, ShiftPatternId>）
     * @return 作成されたスケジュールID
     * @throws IllegalStateException    同一従業員・同一週にスケジュールが既に存在する場合
     * @throws IllegalArgumentException 割当パターンがINACTIVEの場合
     */
    public ScheduleId assignSchedule(
            EmployeeId employeeId,
            LocalDate weekStartDate,
            Map<DayOfWeek, ShiftPatternId> assignments
    ) {
        log.debug("スケジュール割当: employeeId={}, weekStartDate={}", employeeId.value(), weekStartDate);

        // 従業員ID+週開始日の重複をチェックする（1従業員1週1スケジュール）
        if (weeklyScheduleRepository.existsByEmployeeIdAndWeekStartDate(employeeId, weekStartDate)) {
            throw new IllegalStateException(
                    "この従業員の" + weekStartDate + "週のスケジュールは既に登録されています"
            );
        }

        // 割当パターンが全てACTIVEであることを検証する（INV-SH-002）
        validateAllPatternsActive(assignments);

        // WeeklySchedule.assign()でドメインオブジェクトを作成する（DRAFT状態、月曜日チェック含む）
        WeeklySchedule schedule = WeeklySchedule.assign(employeeId, weekStartDate, assignments);

        // リポジトリに保存する（JPAが自動的にpersistを実行）
        WeeklySchedule saved = weeklyScheduleRepository.save(schedule);

        // ShiftAssignedEventを生成する
        ShiftAssignedEvent event = ShiftAssignedEvent.of(
                saved.getId(), saved.getEmployeeId(), saved.getWeekStartDate(),
                saved.getAssignments(), saved.getStatus()
        );

        // イベントストアに追記する（INSERT ONLY）
        persistEvent(saved.getId(), "ASSIGNED", event, event.occurredAt());

        // Springイベントとして発行する（プロジェクターがRead Modelを更新）
        eventPublisher.publishEvent(event);

        log.debug("スケジュール割当完了: scheduleId={}", saved.getId().value());
        return saved.getId();
    }

    // ========================================
    // UC-SH-005: シフトスケジュールを変更する
    // ========================================

    /**
     * 既存の週次スケジュールの割当を変更する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>スケジュールをリポジトリから取得する（存在しなければ例外）</li>
     *   <li>新しい割当パターンが全てACTIVEであることを検証する（INV-SH-002）</li>
     *   <li>変更前のステータスを記録する（イベント用）</li>
     *   <li>集約のchangeAssignments()を呼び出す（PUBLISHEDならDRAFTに戻る）</li>
     *   <li>リポジトリに保存する</li>
     *   <li>CHANGEDイベントをイベントストアに追記する</li>
     *   <li>ShiftChangedEventを発行する（プロジェクターがRead Modelを更新）</li>
     * </ol>
     * </p>
     *
     * @param scheduleId     変更対象のスケジュールID
     * @param newAssignments 新しい曜日ごとの割当
     * @throws IllegalArgumentException スケジュールが見つからない場合、パターンがINACTIVEの場合
     */
    public void changeSchedule(
            ScheduleId scheduleId,
            Map<DayOfWeek, ShiftPatternId> newAssignments
    ) {
        log.debug("スケジュール変更: scheduleId={}", scheduleId.value());

        // スケジュールをリポジトリから取得する（存在しなければ例外）
        WeeklySchedule schedule = findScheduleOrThrow(scheduleId);

        // 新しい割当パターンが全てACTIVEであることを検証する（INV-SH-002）
        validateAllPatternsActive(newAssignments);

        // 変更前のステータスを記録する（イベントに含めるため）
        ScheduleStatus previousStatus = schedule.getStatus();

        // 集約のchangeAssignments()を呼び出す（PUBLISHEDならDRAFTに戻る）
        schedule.changeAssignments(newAssignments);

        // リポジトリに保存する（楽観的ロックでバージョン管理）
        weeklyScheduleRepository.save(schedule);

        // ShiftChangedEventを生成する
        ShiftChangedEvent event = ShiftChangedEvent.of(
                schedule.getId(), schedule.getEmployeeId(),
                schedule.getAssignments(), previousStatus
        );

        // イベントストアに追記する（INSERT ONLY）
        persistEvent(schedule.getId(), "CHANGED", event, event.occurredAt());

        // Springイベントとして発行する（プロジェクターがRead Modelを更新）
        eventPublisher.publishEvent(event);

        log.debug("スケジュール変更完了: scheduleId={}, previousStatus={}, newStatus={}",
                scheduleId.value(), previousStatus, schedule.getStatus());
    }

    // ========================================
    // UC-SH-006: シフトスケジュールを公開する
    // ========================================

    /**
     * 週次スケジュールを公開する
     *
     * <p>処理フロー:
     * <ol>
     *   <li>スケジュールをリポジトリから取得する（存在しなければ例外）</li>
     *   <li>集約のpublish()を呼び出す（ガード: DRAFTであること）</li>
     *   <li>リポジトリに保存する</li>
     *   <li>PUBLISHEDイベントをイベントストアに追記する</li>
     *   <li>ShiftAssignedEventを発行する（ステータスPUBLISHEDで再利用、プロジェクターがRead Modelを更新）</li>
     * </ol>
     * </p>
     *
     * <p>公開されたスケジュールは従業員に通知される（通知機能は将来実装予定）。</p>
     *
     * @param scheduleId 公開対象のスケジュールID
     * @throws IllegalArgumentException スケジュールが見つからない場合
     * @throws IllegalStateException    既にPUBLISHEDの場合
     */
    public void publishSchedule(ScheduleId scheduleId) {
        log.debug("スケジュール公開: scheduleId={}", scheduleId.value());

        // スケジュールをリポジトリから取得する（存在しなければ例外）
        WeeklySchedule schedule = findScheduleOrThrow(scheduleId);

        // 集約のpublish()を呼び出す（ガード: DRAFTであること → PUBLISHED に遷移）
        schedule.publish();

        // リポジトリに保存する（楽観的ロックでバージョン管理）
        weeklyScheduleRepository.save(schedule);

        // ShiftAssignedEventをPUBLISHEDステータスで再利用して生成する
        // ※ プロジェクターがステータス更新を処理する
        ShiftAssignedEvent event = ShiftAssignedEvent.of(
                schedule.getId(), schedule.getEmployeeId(), schedule.getWeekStartDate(),
                schedule.getAssignments(), schedule.getStatus()
        );

        // イベントストアに追記する（INSERT ONLY）
        persistEvent(schedule.getId(), "PUBLISHED", event, event.occurredAt());

        // Springイベントとして発行する（プロジェクターがRead Modelのステータスを更新）
        eventPublisher.publishEvent(event);

        log.debug("スケジュール公開完了: scheduleId={}", scheduleId.value());
    }

    // ========================================
    // ヘルパーメソッド
    // ========================================

    /**
     * 割当パターンが全てACTIVEであることを検証する
     *
     * <p>INV-SH-002: 非アクティブなパターンは新規割当に使用不可。
     * 全パターンをリポジトリから取得し、1つでもINACTIVEがあれば例外をスローする。</p>
     *
     * @param assignments 曜日ごとのパターン割当
     * @throws IllegalArgumentException パターンが見つからない場合、INACTIVEの場合
     */
    private void validateAllPatternsActive(Map<DayOfWeek, ShiftPatternId> assignments) {
        for (Map.Entry<DayOfWeek, ShiftPatternId> entry : assignments.entrySet()) {
            ShiftPatternId patternId = entry.getValue();

            // パターンをリポジトリから取得する（存在しなければ例外）
            ShiftPattern pattern = shiftPatternRepository.findById(patternId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "シフトパターンが見つかりません: " + patternId.value()
                                    + "（" + entry.getKey() + "の割当）"
                    ));

            // ACTIVEであることを検証する（INV-SH-002）
            if (!pattern.isActive()) {
                throw new IllegalArgumentException(
                        "パターン「" + pattern.getName().value() + "」は無効化されているため割当できません"
                                + "（" + entry.getKey() + "の割当）"
                );
            }
        }
    }

    /**
     * スケジュールIDでスケジュールを取得する（見つからない場合は例外をスロー）
     *
     * @param scheduleId スケジュールID
     * @return 週次スケジュール
     * @throws IllegalArgumentException スケジュールが見つからない場合
     */
    private WeeklySchedule findScheduleOrThrow(ScheduleId scheduleId) {
        return weeklyScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "スケジュールが見つかりません: " + scheduleId.value()));
    }

    /**
     * ドメインイベントをイベントストアに保存する
     *
     * <p>イベントオブジェクトをJacksonでJSON文字列に変換し、
     * weekly_schedule_eventsテーブルにINSERTする。</p>
     *
     * @param scheduleId スケジュールID
     * @param eventType  イベント種別（ASSIGNED, CHANGED, PUBLISHED）
     * @param event      ドメインイベントオブジェクト
     * @param occurredAt イベント発生日時
     */
    private void persistEvent(ScheduleId scheduleId, String eventType,
                               Object event, Instant occurredAt) {
        try {
            // イベントオブジェクトをJSON文字列に変換する
            String payloadJson = objectMapper.writeValueAsString(event);
            // イベントストアに追記する（INSERT ONLY）
            weeklyScheduleEventRepository.append(scheduleId, eventType, payloadJson, occurredAt);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "イベントのJSON変換に失敗しました: eventType=" + eventType, e);
        }
    }
}
