package com.example.kintai.attendance.application.command;

import com.example.kintai.attendance.domain.model.AttendanceRecord;
import com.example.kintai.attendance.domain.repository.AttendanceEventRepository;
import com.example.kintai.attendance.domain.repository.AttendanceRecordRepository;
import com.example.kintai.shared.domain.exception.ResourceNotFoundException;
import com.example.kintai.shared.domain.model.AttendanceRecordId;
import com.example.kintai.shared.kernel.contract.DomainEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 勤怠ユースケース共通サポート — 7つの個別UseCaseが共有するヘルパーを提供する
 *
 * <p>以下の共通処理を集約し、各UseCaseからDIして利用する:
 * <ul>
 *   <li>勤怠記録の取得（findRecordOrThrow）</li>
 *   <li>ドメインイベントの永続化・発行（publishAndPersistEvents）</li>
 * </ul>
 * </p>
 */
@Component
public class AttendanceUseCaseSupport {

    /** 勤怠記録リポジトリ — ID検索用 */
    private final AttendanceRecordRepository attendanceRecordRepository;

    /** 勤怠イベントリポジトリ — イベントストアへの追記（INSERT ONLY） */
    private final AttendanceEventRepository attendanceEventRepository;

    /** Springイベント発行 — プロジェクターがRead Modelを更新するトリガー */
    private final ApplicationEventPublisher eventPublisher;

    /** JSONシリアライザ — イベントストアのpayload変換用 */
    private final ObjectMapper objectMapper;

    /**
     * コンストラクタ — 4つの依存を注入する
     */
    public AttendanceUseCaseSupport(
            AttendanceRecordRepository attendanceRecordRepository,
            AttendanceEventRepository attendanceEventRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceEventRepository = attendanceEventRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 勤怠記録をIDで取得する（見つからない場合は例外をスロー）
     *
     * @param id 勤怠記録ID
     * @return 勤怠記録
     * @throws ResourceNotFoundException 勤怠記録が見つからない場合
     */
    public AttendanceRecord findRecordOrThrow(AttendanceRecordId id) {
        return attendanceRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "勤怠記録が見つかりません: " + id.value()));
    }

    /**
     * 集約に蓄積されたドメインイベントを一括で永続化・発行する
     *
     * <p>集約のコマンドメソッドが registerEvent() で登録したイベントを
     * getDomainEvents() で取得し、イベントストアへの記録と Spring イベント発行を行う。
     * 処理完了後に clearDomainEvents() でイベントリストをクリアする。</p>
     *
     * @param record 保存済みの勤怠記録（ドメインイベントが蓄積されている）
     */
    public void publishAndPersistEvents(AttendanceRecord record) {
        // 集約に蓄積されたイベントを順に永続化・発行する
        for (DomainEvent event : record.getDomainEvents()) {
            // イベントストアに記録する
            persistEvent(record.getId(), event);
            // Springイベントとして発行する（プロジェクターがRead Modelを更新）
            eventPublisher.publishEvent(event);
        }
        // イベントリストをクリアして二重永続化を防止する
        record.clearDomainEvents();
    }

    /**
     * ドメインイベントをイベントストアに保存する
     *
     * <p>イベントオブジェクトをJacksonでJSON文字列に変換し、
     * attendance_eventsテーブルにINSERTする。</p>
     *
     * @param attendanceId 勤怠記録ID
     * @param event        ドメインイベント（DomainEvent基底クラス）
     */
    private void persistEvent(AttendanceRecordId attendanceId, DomainEvent event) {
        try {
            // イベントオブジェクトをJSON文字列に変換する
            String payloadJson = objectMapper.writeValueAsString(event);
            // イベントストアに追記する（INSERT ONLY）
            attendanceEventRepository.append(
                    event.getEventId(), attendanceId, event.getEventType(),
                    payloadJson, event.getOccurredAt());
        } catch (JacksonException e) {
            throw new RuntimeException(
                    "イベントのJSON変換に失敗しました: eventType=" + event.getEventType(), e);
        }
    }
}
