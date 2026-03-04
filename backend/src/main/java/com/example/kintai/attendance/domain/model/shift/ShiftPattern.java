package com.example.kintai.attendance.domain.model.shift;

import com.example.kintai.shared.domain.model.ShiftPatternId;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * シフトパターン — 勤務時間帯の定義を管理するエンティティ
 *
 * <p>「早番: 6:00-15:00」「遅番: 14:00-23:00」「夜勤: 22:00-翌7:00」のように、
 * 勤務の開始時刻・終了時刻・休憩時間をパターンとして定義する。
 * 週次スケジュールで各曜日にパターンを割り当てて使用する。</p>
 *
 * <p>不変条件:
 * <ul>
 *   <li>INV-SH-001: パターン名はシステム全体で一意</li>
 *   <li>INV-SH-002: 非アクティブなパターンは新規割当に使用不可</li>
 * </ul>
 * </p>
 */
public class ShiftPattern {

    /** シフトパターンID */
    private final ShiftPatternId id;

    /** パターン名（一意） */
    private final PatternName name;

    /** 勤務開始時刻 */
    private final LocalTime startTime;

    /** 勤務終了時刻 */
    private final LocalTime endTime;

    /** 休憩時間（分） */
    private final int breakMinutes;

    /** 夜勤フラグ — trueの場合、日跨ぎを許容する */
    private final boolean isOvernight;

    /** 有効フラグ — falseの場合、新規割当に使用不可 */
    private boolean isActive;

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
    ShiftPattern(
            ShiftPatternId id,
            PatternName name,
            LocalTime startTime,
            LocalTime endTime,
            int breakMinutes,
            boolean isOvernight,
            boolean isActive,
            int version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "シフトパターンIDはnullにできません");
        this.name = Objects.requireNonNull(name, "パターン名はnullにできません");
        this.startTime = Objects.requireNonNull(startTime, "勤務開始時刻はnullにできません");
        this.endTime = Objects.requireNonNull(endTime, "勤務終了時刻はnullにできません");
        this.breakMinutes = breakMinutes;
        this.isOvernight = isOvernight;
        this.isActive = isActive;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "作成日時はnullにできません");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新日時はnullにできません");
    }

    // ========================
    // DB復元用ファクトリメソッド
    // ========================

    /**
     * DBから読み込んだデータでシフトパターンを復元する — リポジトリ実装専用
     */
    public static ShiftPattern reconstruct(
            ShiftPatternId id, PatternName name,
            LocalTime startTime, LocalTime endTime,
            int breakMinutes, boolean isOvernight, boolean isActive,
            int version, Instant createdAt, Instant updatedAt
    ) {
        return new ShiftPattern(
                id, name, startTime, endTime, breakMinutes,
                isOvernight, isActive, version, createdAt, updatedAt
        );
    }

    // ========================
    // ファクトリメソッド
    // ========================

    /**
     * 新規シフトパターンを定義する
     *
     * <p>パターンは初期状態でACTIVE（有効）として作成される。
     * パターン名の一意性チェックはアプリケーション層で行う。</p>
     *
     * @param name         パターン名
     * @param startTime    勤務開始時刻
     * @param endTime      勤務終了時刻
     * @param breakMinutes 休憩時間（分）
     * @param isOvernight  夜勤フラグ
     * @return 新規ShiftPatternインスタンス
     */
    public static ShiftPattern define(
            PatternName name,
            LocalTime startTime,
            LocalTime endTime,
            int breakMinutes,
            boolean isOvernight
    ) {
        // 休憩時間のバリデーション（0〜120分）
        if (breakMinutes < 0 || breakMinutes > 120) {
            throw new IllegalArgumentException(
                    "休憩時間は0〜120分で指定してください（現在: " + breakMinutes + "分）"
            );
        }

        Instant now = Instant.now();
        return new ShiftPattern(
                ShiftPatternId.generate(),
                name,
                startTime,
                endTime,
                breakMinutes,
                isOvernight,
                true,  // 初期状態はACTIVE
                0,
                now,
                now
        );
    }

    // ========================
    // コマンドメソッド
    // ========================

    /**
     * パターンを無効化する
     *
     * <p>ガード条件: 現在ACTIVEであること。
     * 無効化されたパターンは新規割当に使用できなくなる。
     * 既存の割当には影響しない。</p>
     *
     * @throws IllegalStateException 既にINACTIVEの場合
     */
    public void deactivate() {
        // ガード条件: 有効状態であることを確認
        if (!isActive) {
            throw new IllegalStateException("パターンは既に無効化されています");
        }

        // 有効フラグをOFFにする
        isActive = false;
        updatedAt = Instant.now();
    }

    /**
     * パターンを再有効化する
     *
     * <p>ガード条件: 現在INACTIVEであること。</p>
     *
     * @throws IllegalStateException 既にACTIVEの場合
     */
    public void reactivate() {
        // ガード条件: 無効状態であることを確認
        if (isActive) {
            throw new IllegalStateException("パターンは既に有効です");
        }

        // 有効フラグをONにする
        isActive = true;
        updatedAt = Instant.now();
    }

    // ========================
    // 計算メソッド
    // ========================

    /**
     * 所定労働時間（分）を算出する
     *
     * <p>計算式:
     * <ul>
     *   <li>通常: (endTime - startTime) - breakMinutes</li>
     *   <li>夜勤: (24h - startTime + endTime) - breakMinutes</li>
     * </ul>
     * </p>
     *
     * @return 所定労働時間（分）
     */
    public int calculateScheduledMinutes() {
        long totalMinutes;

        if (isOvernight) {
            // 夜勤: 開始から翌日終了までの分数
            totalMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
            if (totalMinutes <= 0) {
                totalMinutes += 24 * 60; // 日跨ぎ補正
            }
        } else {
            // 通常: 終了 - 開始
            totalMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
        }

        return (int) totalMinutes - breakMinutes;
    }

    // ========================
    // ゲッター
    // ========================

    public ShiftPatternId getId() { return id; }
    public PatternName getName() { return name; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getBreakMinutes() { return breakMinutes; }
    public boolean isOvernight() { return isOvernight; }
    public boolean isActive() { return isActive; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
