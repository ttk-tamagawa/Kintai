package com.example.kintai.attendance.domain.repository;

import com.example.kintai.shared.domain.model.AttendanceRecordId;

import java.time.Instant;

/**
 * 勤怠イベントリポジトリ — ドメインイベントの追記専用リポジトリ（INSERT ONLY）
 *
 * <p>一度記録されたイベントは変更・削除しない。
 * イベントはattendance_eventsテーブルにJSONB形式で保存される。
 * アプリケーション層がドメインイベントをJSON文字列に変換して渡す。</p>
 */
public interface AttendanceEventRepository {

    /**
     * ドメインイベントをイベントストアに追記する
     *
     * @param eventId      ドメインイベントID（DomainEvent基底クラスで自動生成されたUUID）
     * @param attendanceId 対象の勤怠記録ID
     * @param eventType    イベント種別（CLOCKED_IN, CLOCKED_OUT 等）
     * @param payloadJson  イベントデータのJSON文字列
     * @param occurredAt   イベント発生日時
     */
    void append(java.util.UUID eventId, AttendanceRecordId attendanceId, String eventType, String payloadJson, Instant occurredAt);
}
