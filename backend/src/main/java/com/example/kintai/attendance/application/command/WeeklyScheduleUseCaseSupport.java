package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.ShiftPattern;
import com.example.kintai.attendance.domain.model.shift.WeeklySchedule;
import com.example.kintai.attendance.domain.repository.ShiftPatternRepository;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleEventRepository;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleRepository;
import com.example.kintai.shared.domain.exception.BusinessRuleViolationException;
import com.example.kintai.shared.domain.exception.ResourceNotFoundException;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.domain.model.ShiftPatternId;
import com.example.kintai.shared.kernel.contract.DomainEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 週次スケジュールユースケース共通サポート — 4つの個別UseCaseが共有するヘルパーを提供する
 *
 * <p>以下の共通処理を集約し、各UseCaseからDIして利用する:
 * <ul>
 *   <li>スケジュールの取得（findScheduleOrThrow）</li>
 *   <li>ドメインイベントの永続化・発行（publishAndPersistEvents）</li>
 *   <li>割当パターンの検証・パターン名収集（validateAndCollectPatternNames）</li>
 * </ul>
 * </p>
 */
@Component
public class WeeklyScheduleUseCaseSupport {

    /** 週次スケジュールリポジトリ — ID検索用 */
    private final WeeklyScheduleRepository weeklyScheduleRepository;

    /** スケジュールイベントリポジトリ — イベントストアへの追記（INSERT ONLY） */
    private final WeeklyScheduleEventRepository weeklyScheduleEventRepository;

    /** シフトパターンリポジトリ — パターンのACTIVE検証用（一括取得でN+1回避） */
    private final ShiftPatternRepository shiftPatternRepository;

    /** Springイベント発行 — プロジェクターがRead Modelを更新するトリガー */
    private final ApplicationEventPublisher eventPublisher;

    /** JSONシリアライザ — イベントストアのpayload変換用 */
    private final ObjectMapper objectMapper;

    /**
     * コンストラクタ — 5つの依存を注入する
     */
    public WeeklyScheduleUseCaseSupport(
            WeeklyScheduleRepository weeklyScheduleRepository,
            WeeklyScheduleEventRepository weeklyScheduleEventRepository,
            ShiftPatternRepository shiftPatternRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.weeklyScheduleEventRepository = weeklyScheduleEventRepository;
        this.shiftPatternRepository = shiftPatternRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * スケジュールIDでスケジュールを取得する（見つからない場合は例外をスロー）
     *
     * @param scheduleId スケジュールID
     * @return 週次スケジュール
     * @throws ResourceNotFoundException スケジュールが見つからない場合
     */
    public WeeklySchedule findScheduleOrThrow(ScheduleId scheduleId) {
        return weeklyScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "スケジュールが見つかりません: " + scheduleId.value()));
    }

    /**
     * 割当パターンが全てACTIVEであることを検証し、パターン名を収集する
     *
     * <p>INV-SH-002: 非アクティブなパターンは新規割当に使用不可。
     * パターンIDを重複排除してから一括取得（findAllById）することで、
     * 曜日数ぶんの個別SELECTを1回のクエリに集約する（N+1回避）。</p>
     *
     * @param assignments 曜日ごとのパターン割当
     * @return パターンID→パターン名のマップ
     * @throws ResourceNotFoundException      パターンが見つからない場合
     * @throws BusinessRuleViolationException パターンがINACTIVEの場合
     */
    public Map<ShiftPatternId, String> validateAndCollectPatternNames(
            Map<DayOfWeek, ShiftPatternId> assignments) {

        // ---- 1. 割当中の全パターンIDを重複排除して一括取得する（N+1回避） ----
        Set<ShiftPatternId> patternIds = new HashSet<>(assignments.values());
        List<ShiftPattern> patterns = shiftPatternRepository.findAllById(patternIds);

        // ID→Patternのマップに変換してO(1)ルックアップ可能にする
        Map<ShiftPatternId, ShiftPattern> patternMap = patterns.stream()
                .collect(Collectors.toMap(ShiftPattern::getId, p -> p));

        // ---- 2. 曜日ごとの割当をループし、存在チェック＆ACTIVE検証を行う ----
        Map<ShiftPatternId, String> patternNames = new HashMap<>();
        for (Map.Entry<DayOfWeek, ShiftPatternId> entry : assignments.entrySet()) {
            ShiftPatternId patternId = entry.getValue();
            ShiftPattern pattern = patternMap.get(patternId);

            // パターンが存在しなければ例外（どの曜日の割当で失敗したかを示す）
            if (pattern == null) {
                throw new ResourceNotFoundException(
                        "シフトパターンが見つかりません: " + patternId.value()
                                + "（" + entry.getKey() + "の割当）");
            }

            // ACTIVEであることを検証する（INV-SH-002）
            if (!pattern.isActive()) {
                throw new BusinessRuleViolationException(
                        "パターン「" + pattern.getName().value() + "」は無効化されているため割当できません"
                                + "（" + entry.getKey() + "の割当）");
            }

            // パターン名を収集する（レスポンス構築用）
            patternNames.put(patternId, pattern.getName().value());
        }

        return patternNames;
    }

    /**
     * 集約に蓄積されたドメインイベントを一括で永続化・発行する
     *
     * <p>集約のコマンドメソッドが registerEvent() で登録したイベントを
     * getDomainEvents() で取得し、イベントストアへの記録と Spring イベント発行を行う。
     * 処理完了後に clearDomainEvents() でイベントリストをクリアする。</p>
     *
     * @param schedule 保存済みの週次スケジュール（ドメインイベントが蓄積されている）
     */
    public void publishAndPersistEvents(WeeklySchedule schedule) {
        // 集約に蓄積されたイベントを順に永続化・発行する
        for (DomainEvent event : schedule.getDomainEvents()) {
            // イベントストアに記録する
            persistEvent(schedule.getId(), event);
            // Springイベントとして発行する（プロジェクターがRead Modelを更新）
            eventPublisher.publishEvent(event);
        }
        // イベントリストをクリアして二重永続化を防止する
        schedule.clearDomainEvents();
    }

    /**
     * ドメインイベントをイベントストアに保存する
     *
     * <p>イベントオブジェクトをJacksonでJSON文字列に変換し、
     * weekly_schedule_eventsテーブルにINSERTする。</p>
     *
     * @param scheduleId スケジュールID
     * @param event      ドメインイベント（DomainEvent基底クラス）
     */
    private void persistEvent(ScheduleId scheduleId, DomainEvent event) {
        try {
            // イベントオブジェクトをJSON文字列に変換する
            String payloadJson = objectMapper.writeValueAsString(event);
            // イベントストアに追記する（INSERT ONLY）
            weeklyScheduleEventRepository.append(
                    event.getEventId(), scheduleId, event.getEventType(),
                    payloadJson, event.getOccurredAt());
        } catch (JacksonException e) {
            throw new RuntimeException(
                    "イベントのJSON変換に失敗しました: eventType=" + event.getEventType(), e);
        }
    }
}
