package com.example.kintai.attendance.domain.model.shift.event;

import com.example.kintai.attendance.domain.model.shift.PatternName;
import com.example.kintai.shared.domain.model.ShiftPatternId;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;

/**
 * シフトパターン定義イベント — 新しいシフトパターンが作成されたときに発行される
 *
 * <p>このイベントが発行されると、以下の後続処理が行われる:
 * <ul>
 *   <li>Read Model（weekly_schedule_summaries）のパターン名マスタを更新</li>
 *   <li>管理者への作成完了通知</li>
 * </ul>
 * </p>
 *
 * @param patternId   シフトパターンID
 * @param name        パターン名（早番、遅番等）
 * @param startTime   勤務開始時刻
 * @param endTime     勤務終了時刻
 * @param isOvernight 夜勤フラグ（trueなら日跨ぎパターン）
 * @param occurredAt  イベント発生日時
 */
public record ShiftPatternDefinedEvent(
        ShiftPatternId patternId,
        PatternName name,
        LocalTime startTime,
        LocalTime endTime,
        boolean isOvernight,
        Instant occurredAt
) {

    /**
     * コンパクトコンストラクタ — 全フィールドのnullチェックを行う
     */
    public ShiftPatternDefinedEvent {
        Objects.requireNonNull(patternId, "シフトパターンIDはnullにできません");
        Objects.requireNonNull(name, "パターン名はnullにできません");
        Objects.requireNonNull(startTime, "勤務開始時刻はnullにできません");
        Objects.requireNonNull(endTime, "勤務終了時刻はnullにできません");
        // isOvernight はプリミティブ型のためnullチェック不要
        Objects.requireNonNull(occurredAt, "イベント発生日時はnullにできません");
    }

    /**
     * イベントを生成するファクトリメソッド
     *
     * <p>ShiftPatternのdefine()成功後に呼び出される。
     * イベント発生日時は自動的に現在時刻が設定される。</p>
     *
     * @param patternId   シフトパターンID
     * @param name        パターン名
     * @param startTime   勤務開始時刻
     * @param endTime     勤務終了時刻
     * @param isOvernight 夜勤フラグ
     * @return ShiftPatternDefinedEventインスタンス
     */
    public static ShiftPatternDefinedEvent of(
            ShiftPatternId patternId,
            PatternName name,
            LocalTime startTime,
            LocalTime endTime,
            boolean isOvernight
    ) {
        return new ShiftPatternDefinedEvent(
                patternId,
                name,
                startTime,
                endTime,
                isOvernight,
                Instant.now()
        );
    }
}
