package com.example.kintai.shared.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * スケジュールID — 週次スケジュールを一意に識別する型安全なID
 *
 * @param value UUID値（null不可）
 */
public record ScheduleId(UUID value) {

    /** null値を拒否する */
    public ScheduleId {
        Objects.requireNonNull(value, "ScheduleIdのvalueはnullにできません");
    }

    /** 既存のUUIDからIDを生成する */
    public static ScheduleId of(UUID value) {
        return new ScheduleId(value);
    }

    /** 新規IDを自動生成する */
    public static ScheduleId generate() {
        return new ScheduleId(UUID.randomUUID());
    }
}
