package com.example.kintai.attendance.domain.repository;

import com.example.kintai.shared.domain.model.ScheduleId;

import java.time.Instant;

/**
 * スケジュールイベントリポジトリ — スケジュールイベントの追記専用リポジトリ（INSERT ONLY）
 *
 * <p>一度記録されたイベントは変更・削除しない。
 * 4種類のイベント（ASSIGNED, CHANGED, PUBLISHED, UNPUBLISHED）を記録する。</p>
 */
public interface WeeklyScheduleEventRepository {

    /**
     * スケジュールイベントをイベントストアに追記する
     *
     * @param eventId     ドメインイベントID（DomainEvent基底クラスで自動生成されたUUID）
     * @param scheduleId  対象のスケジュールID
     * @param eventType   イベント種別（ASSIGNED, CHANGED, PUBLISHED, UNPUBLISHED）
     * @param payloadJson イベントデータのJSON文字列
     * @param occurredAt  イベント発生日時
     */
    void append(java.util.UUID eventId, ScheduleId scheduleId, String eventType, String payloadJson, Instant occurredAt);
}
