package com.example.kintai.attendance.domain.model.shift;

import com.example.kintai.attendance.domain.model.shift.event.ShiftPatternDeactivatedEvent;
import com.example.kintai.attendance.domain.model.shift.event.ShiftPatternDefinedEvent;
import com.example.kintai.attendance.domain.model.shift.event.ShiftPatternReactivatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ShiftPattern（シフトパターン）のユニットテスト
 *
 * <p>パターン定義・有効/無効切替・夜勤判定・所定労働時間計算を検証する。
 * Spring / Mockito を使用しない純粋なユニットテスト。</p>
 */
@DisplayName("ShiftPattern テスト")
class ShiftPatternTest {

    // ========================
    // テストヘルパーメソッド
    // ========================

    /** 標準的な日勤パターン（9:00-18:00、休憩60分）を生成するヘルパー */
    private ShiftPattern createStandardDayPattern() {
        return ShiftPattern.define(
                new PatternName("日勤"),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                60,
                false
        );
    }

    /** 夜勤パターン（22:00-翌7:00、休憩60分）を生成するヘルパー */
    private ShiftPattern createOvernightPattern() {
        return ShiftPattern.define(
                new PatternName("夜勤"),
                LocalTime.of(22, 0),
                LocalTime.of(7, 0),
                60,
                true
        );
    }

    /** 指定した休憩時間でパターンを生成するヘルパー */
    private ShiftPattern createPatternWithBreak(int breakMinutes) {
        return ShiftPattern.define(
                new PatternName("テスト"),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                breakMinutes,
                false
        );
    }

    // ========================
    // define() テスト
    // ========================

    @Nested
    @DisplayName("define()（パターン定義）")
    class DefineTest {

        @Test
        @DisplayName("正常系: 新規パターンはACTIVE=trueで作成される")
        void 新規パターンはACTIVEで作成される() {
            // 実行: 標準日勤パターンを定義
            ShiftPattern pattern = createStandardDayPattern();

            // 検証: 初期状態でACTIVEであること
            assertTrue(pattern.isActive());

            // ドメインイベントの検証: ShiftPatternDefinedEvent が登録されていること
            assertEquals(1, pattern.getDomainEvents().size(),
                    "ドメインイベントが1件登録されること");
            assertInstanceOf(ShiftPatternDefinedEvent.class, pattern.getDomainEvents().getFirst(),
                    "ShiftPatternDefinedEvent が登録されること");
        }

        @Test
        @DisplayName("正常系: 新規パターンはversion=0で作成される")
        void 新規パターンはバージョン0で作成される() {
            // 実行: 標準日勤パターンを定義
            ShiftPattern pattern = createStandardDayPattern();

            // 検証: 初期バージョンが0であること
            assertEquals(0, pattern.getVersion());
        }

        @Test
        @DisplayName("正常系: IDが自動生成される")
        void IDが自動生成される() {
            // 実行: パターンを定義
            ShiftPattern pattern = createStandardDayPattern();

            // 検証: IDがnullでないこと
            assertNotNull(pattern.getId());
            assertNotNull(pattern.getId().value());
        }

        @Test
        @DisplayName("正常系: 各フィールドが正しく設定される")
        void 各フィールドが正しく設定される() {
            // 実行: 日勤パターンを定義
            ShiftPattern pattern = ShiftPattern.define(
                    new PatternName("早番"),
                    LocalTime.of(6, 0),
                    LocalTime.of(15, 0),
                    60,
                    false
            );

            // 検証: 各フィールドが正しいこと
            assertEquals("早番", pattern.getName().value());
            assertEquals(LocalTime.of(6, 0), pattern.getStartTime());
            assertEquals(LocalTime.of(15, 0), pattern.getEndTime());
            assertEquals(60, pattern.getBreakMinutes());
            assertFalse(pattern.isOvernight());
        }

        @Test
        @DisplayName("正常系: 作成日時・更新日時が設定される")
        void 作成日時と更新日時が設定される() {
            // 実行: パターンを定義
            ShiftPattern pattern = createStandardDayPattern();

            // 検証: タイムスタンプがnullでないこと
            assertNotNull(pattern.getCreatedAt());
            assertNotNull(pattern.getUpdatedAt());
        }

        @Test
        @DisplayName("正常系: 休憩時間0分は有効")
        void 休憩時間0分は有効() {
            // 実行・検証: 休憩0分で例外が発生しないこと
            ShiftPattern pattern = assertDoesNotThrow(
                    () -> createPatternWithBreak(0)
            );
            assertEquals(0, pattern.getBreakMinutes());
        }

        @Test
        @DisplayName("正常系: 休憩時間120分は有効")
        void 休憩時間120分は有効() {
            // 実行・検証: 休憩120分で例外が発生しないこと
            ShiftPattern pattern = assertDoesNotThrow(
                    () -> createPatternWithBreak(120)
            );
            assertEquals(120, pattern.getBreakMinutes());
        }

        @Test
        @DisplayName("異常系: 休憩時間-1分はIllegalArgumentExceptionが発生する")
        void 休憩時間がマイナスだと例外が発生する() {
            // 実行・検証: 休憩-1分で例外が発生すること
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> createPatternWithBreak(-1)
            );
            assertTrue(exception.getMessage().contains("-1"));
        }

        @Test
        @DisplayName("異常系: 休憩時間121分はIllegalArgumentExceptionが発生する")
        void 休憩時間が上限超過だと例外が発生する() {
            // 実行・検証: 休憩121分で例外が発生すること
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> createPatternWithBreak(121)
            );
            assertTrue(exception.getMessage().contains("121"));
        }
    }

    // ========================
    // deactivate() テスト
    // ========================

    @Nested
    @DisplayName("deactivate()（パターン無効化）")
    class DeactivateTest {

        @Test
        @DisplayName("正常系: ACTIVE→INACTIVEに遷移する")
        void ACTIVEからINACTIVEに遷移する() {
            // 準備: ACTIVEなパターンを作成
            ShiftPattern pattern = createStandardDayPattern();
            assertTrue(pattern.isActive());

            // 実行: 無効化する
            pattern.deactivate();

            // 検証: INACTIVEになっていること
            assertFalse(pattern.isActive());
        }

        @Test
        @DisplayName("正常系: 無効化時にupdatedAtが更新される")
        void 無効化時にupdatedAtが更新される() {
            // 準備: パターンを作成し初期のupdatedAtを記録
            ShiftPattern pattern = createStandardDayPattern();
            var beforeUpdate = pattern.getUpdatedAt();

            // 少し待ってから無効化（時刻の変化を保証するため）
            pattern.deactivate();

            // 検証: updatedAtが更新されていること（同じか後の時刻）
            assertNotNull(pattern.getUpdatedAt());
            assertTrue(
                    pattern.getUpdatedAt().equals(beforeUpdate)
                            || pattern.getUpdatedAt().isAfter(beforeUpdate)
            );
        }

        @Test
        @DisplayName("異常系: 既にINACTIVEだとIllegalStateExceptionが発生する")
        void 既にINACTIVEだと例外が発生する() {
            // 準備: パターンを無効化済みにする
            ShiftPattern pattern = createStandardDayPattern();
            pattern.deactivate();

            // 実行・検証: 再度無効化しようとすると例外が発生すること
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> pattern.deactivate()
            );
            assertEquals("パターンは既に無効化されています", exception.getMessage());
        }

        @Test
        @DisplayName("正常系: 無効化時に ShiftPatternDeactivatedEvent が登録される")
        void 無効化時にイベントが登録される() {
            // 準備: ACTIVEなパターンを作成（define で 1 件イベント登録済み）
            ShiftPattern pattern = createStandardDayPattern();

            // 実行: 無効化する
            pattern.deactivate();

            // 検証: ドメインイベントが計 2 件（Defined + Deactivated）登録されていること
            assertEquals(2, pattern.getDomainEvents().size(),
                    "Defined + Deactivated の 2 件が登録されること");
            assertInstanceOf(ShiftPatternDeactivatedEvent.class,
                    pattern.getDomainEvents().get(1),
                    "2 件目は ShiftPatternDeactivatedEvent であること");
        }
    }

    // ========================
    // reactivate() テスト
    // ========================

    @Nested
    @DisplayName("reactivate()（パターン再有効化）")
    class ReactivateTest {

        @Test
        @DisplayName("正常系: INACTIVE→ACTIVEに遷移する")
        void INACTIVEからACTIVEに遷移する() {
            // 準備: パターンを一度無効化する
            ShiftPattern pattern = createStandardDayPattern();
            pattern.deactivate();
            assertFalse(pattern.isActive());

            // 実行: 再有効化する
            pattern.reactivate();

            // 検証: ACTIVEに戻っていること
            assertTrue(pattern.isActive());
        }

        @Test
        @DisplayName("正常系: 再有効化時にupdatedAtが更新される")
        void 再有効化時にupdatedAtが更新される() {
            // 準備: パターンを無効化してからupdatedAtを記録
            ShiftPattern pattern = createStandardDayPattern();
            pattern.deactivate();
            var beforeUpdate = pattern.getUpdatedAt();

            // 実行: 再有効化する
            pattern.reactivate();

            // 検証: updatedAtが更新されていること
            assertNotNull(pattern.getUpdatedAt());
            assertTrue(
                    pattern.getUpdatedAt().equals(beforeUpdate)
                            || pattern.getUpdatedAt().isAfter(beforeUpdate)
            );
        }

        @Test
        @DisplayName("異常系: 既にACTIVEだとIllegalStateExceptionが発生する")
        void 既にACTIVEだと例外が発生する() {
            // 準備: ACTIVEなパターン（初期状態）
            ShiftPattern pattern = createStandardDayPattern();

            // 実行・検証: ACTIVEの状態で再有効化しようとすると例外が発生すること
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> pattern.reactivate()
            );
            assertEquals("パターンは既に有効です", exception.getMessage());
        }

        @Test
        @DisplayName("正常系: 再有効化時に ShiftPatternReactivatedEvent が登録される")
        void 再有効化時にイベントが登録される() {
            // 準備: パターンを無効化した状態にする（Defined + Deactivated の 2 件登録済み）
            ShiftPattern pattern = createStandardDayPattern();
            pattern.deactivate();

            // 実行: 再有効化する
            pattern.reactivate();

            // 検証: ドメインイベントが計 3 件（Defined + Deactivated + Reactivated）登録されていること
            assertEquals(3, pattern.getDomainEvents().size(),
                    "Defined + Deactivated + Reactivated の 3 件が登録されること");
            assertInstanceOf(ShiftPatternReactivatedEvent.class,
                    pattern.getDomainEvents().get(2),
                    "3 件目は ShiftPatternReactivatedEvent であること");
        }
    }

    // ========================
    // 夜勤パターン テスト
    // ========================

    @Nested
    @DisplayName("夜勤パターン")
    class OvernightPatternTest {

        @Test
        @DisplayName("正常系: isOvernight=trueで夜勤パターンが作成される")
        void 夜勤パターンが作成される() {
            // 実行: 夜勤パターンを定義
            ShiftPattern pattern = createOvernightPattern();

            // 検証: isOvernightがtrueであること
            assertTrue(pattern.isOvernight());
        }

        @Test
        @DisplayName("正常系: 通常パターンはisOvernight=false")
        void 通常パターンはisOvernightがfalse() {
            // 実行: 日勤パターンを定義
            ShiftPattern pattern = createStandardDayPattern();

            // 検証: isOvernightがfalseであること
            assertFalse(pattern.isOvernight());
        }

        @Test
        @DisplayName("夜勤パターンの時刻設定: 開始22:00・終了7:00が正しく保持される")
        void 夜勤パターンの時刻が正しく保持される() {
            // 実行: 夜勤パターンを定義
            ShiftPattern pattern = createOvernightPattern();

            // 検証: 開始・終了時刻が正しいこと
            assertEquals(LocalTime.of(22, 0), pattern.getStartTime());
            assertEquals(LocalTime.of(7, 0), pattern.getEndTime());
        }
    }

    // ========================
    // calculateScheduledMinutes() テスト
    // ========================

    @Nested
    @DisplayName("calculateScheduledMinutes()（所定労働時間計算）")
    class CalculateScheduledMinutesTest {

        @Test
        @DisplayName("通常パターン: 9:00-18:00（休憩60分）= 480分")
        void 通常パターンの所定労働時間を計算する() {
            // 準備: 9:00-18:00、休憩60分 → (18:00 - 9:00) - 60 = 540 - 60 = 480分
            ShiftPattern pattern = createStandardDayPattern();

            // 実行: 所定労働時間を計算
            int minutes = pattern.calculateScheduledMinutes();

            // 検証: 480分（8時間）であること
            assertEquals(480, minutes);
        }

        @Test
        @DisplayName("通常パターン: 6:00-15:00（休憩60分）= 480分")
        void 早番パターンの所定労働時間を計算する() {
            // 準備: 早番パターンを定義
            ShiftPattern pattern = ShiftPattern.define(
                    new PatternName("早番"),
                    LocalTime.of(6, 0),
                    LocalTime.of(15, 0),
                    60,
                    false
            );

            // 実行: 所定労働時間を計算
            int minutes = pattern.calculateScheduledMinutes();

            // 検証: (15:00 - 6:00) - 60 = 540 - 60 = 480分
            assertEquals(480, minutes);
        }

        @Test
        @DisplayName("通常パターン: 休憩0分の場合は全時間が労働時間")
        void 休憩なしの場合() {
            // 準備: 9:00-18:00、休憩0分
            ShiftPattern pattern = ShiftPattern.define(
                    new PatternName("休憩なし"),
                    LocalTime.of(9, 0),
                    LocalTime.of(18, 0),
                    0,
                    false
            );

            // 実行: 所定労働時間を計算
            int minutes = pattern.calculateScheduledMinutes();

            // 検証: (18:00 - 9:00) - 0 = 540分（9時間）
            assertEquals(540, minutes);
        }

        @Test
        @DisplayName("夜勤パターン: 22:00-翌7:00（休憩60分）= 480分")
        void 夜勤パターンの所定労働時間を計算する() {
            // 準備: 22:00-翌7:00、休憩60分 → (24h - 22:00 + 7:00) - 60 = 540 - 60 = 480分
            ShiftPattern pattern = createOvernightPattern();

            // 実行: 所定労働時間を計算
            int minutes = pattern.calculateScheduledMinutes();

            // 検証: 480分（8時間）であること
            assertEquals(480, minutes);
        }

        @Test
        @DisplayName("夜勤パターン: 21:00-翌6:00（休憩60分）= 480分")
        void 別の夜勤パターンの所定労働時間を計算する() {
            // 準備: 21:00-翌6:00、休憩60分
            ShiftPattern pattern = ShiftPattern.define(
                    new PatternName("準夜勤"),
                    LocalTime.of(21, 0),
                    LocalTime.of(6, 0),
                    60,
                    true
            );

            // 実行: 所定労働時間を計算
            int minutes = pattern.calculateScheduledMinutes();

            // 検証: (24h - 21:00 + 6:00) - 60 = 540 - 60 = 480分
            assertEquals(480, minutes);
        }

        @Test
        @DisplayName("遅番パターン: 14:00-23:00（休憩60分）= 480分")
        void 遅番パターンの所定労働時間を計算する() {
            // 準備: 遅番パターンを定義
            ShiftPattern pattern = ShiftPattern.define(
                    new PatternName("遅番"),
                    LocalTime.of(14, 0),
                    LocalTime.of(23, 0),
                    60,
                    false
            );

            // 実行: 所定労働時間を計算
            int minutes = pattern.calculateScheduledMinutes();

            // 検証: (23:00 - 14:00) - 60 = 540 - 60 = 480分
            assertEquals(480, minutes);
        }
    }

    // ========================
    // 状態遷移サイクル テスト
    // ========================

    @Nested
    @DisplayName("状態遷移サイクル")
    class StateCycleTest {

        @Test
        @DisplayName("ACTIVE → INACTIVE → ACTIVE の遷移サイクルが正常に動作する")
        void 有効無効の切替サイクルが正常に動作する() {
            // 準備: ACTIVEなパターンを作成
            ShiftPattern pattern = createStandardDayPattern();
            assertTrue(pattern.isActive());

            // 実行1: ACTIVE → INACTIVE
            pattern.deactivate();
            assertFalse(pattern.isActive());

            // 実行2: INACTIVE → ACTIVE
            pattern.reactivate();
            assertTrue(pattern.isActive());

            // 実行3: 再度 ACTIVE → INACTIVE
            pattern.deactivate();
            assertFalse(pattern.isActive());
        }
    }
}
