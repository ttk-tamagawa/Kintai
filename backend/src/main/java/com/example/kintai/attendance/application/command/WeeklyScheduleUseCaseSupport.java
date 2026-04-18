package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.shift.WeeklySchedule;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleEventRepository;
import com.example.kintai.attendance.domain.repository.WeeklyScheduleRepository;
import com.example.kintai.shared.domain.exception.ResourceNotFoundException;
import com.example.kintai.shared.domain.model.ScheduleId;
import com.example.kintai.shared.kernel.contract.DomainEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 週次スケジュールユースケース共通サポート — 4つの個別UseCaseが共有するヘルパーを提供する
 *
 * <p>以下の共通処理を集約し、各UseCaseからDIして利用する:
 * <ul>
 *   <li>スケジュールの取得（findScheduleOrThrow）</li>
 *   <li>ドメインイベントの永続化・発行（publishAndPersistEvents）</li>
 * </ul>
 * </p>
 */
@Component
public class WeeklyScheduleUseCaseSupport {

    /** 週次スケジュールリポジトリ — ID検索用 */
    private final WeeklyScheduleRepository weeklyScheduleRepository;

    /** スケジュールイベントリポジトリ — イベントストアへの追記（INSERT ONLY） */
    private final WeeklyScheduleEventRepository weeklyScheduleEventRepository;

    /** Springイベント発行 — プロジェクターがRead Modelを更新するトリガー */
    private final ApplicationEventPublisher eventPublisher;

    /** JSONシリアライザ — イベントストアのpayload変換用 */
    private final ObjectMapper objectMapper;

    /**
     * コンストラクタ — 4つの依存を注入する
     */
    public WeeklyScheduleUseCaseSupport(
            WeeklyScheduleRepository weeklyScheduleRepository,
            WeeklyScheduleEventRepository weeklyScheduleEventRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.weeklyScheduleEventRepository = weeklyScheduleEventRepository;
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
