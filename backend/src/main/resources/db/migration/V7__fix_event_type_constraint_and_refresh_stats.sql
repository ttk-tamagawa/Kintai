-- ============================================
-- V7: イベント型CHECK制約修正 + マテリアライズドビュー初回ポピュレート
-- ============================================

-- === 1. attendance_events の CHECK制約を修正する ===
-- ドメインイベント名とDB制約名を一致させる
-- 変更対象:
--   DURATION_CALCULATED → WORK_DURATION_CALCULATED
--   MANUAL_REGISTERED → MANUAL_ATTENDANCE_REGISTERED
--   FINALIZED → ATTENDANCE_FINALIZED

ALTER TABLE attendance_events DROP CONSTRAINT chk_attendance_events_type;

ALTER TABLE attendance_events ADD CONSTRAINT chk_attendance_events_type
    CHECK (event_type IN (
        'CLOCKED_IN', 'CLOCKED_OUT',
        'BREAK_STARTED', 'BREAK_ENDED',
        'WORK_DURATION_CALCULATED', 'CLOCK_CORRECTED',
        'MANUAL_ATTENDANCE_REGISTERED', 'ATTENDANCE_FINALIZED'
    ));

-- === 2. department_attendance_stats マテリアライズドビューを初回ポピュレートする ===
-- V5で WITH NO DATA として作成されたため、REFRESH しないとSELECTが失敗する
-- 初回は CONCURRENTLY なしでリフレッシュする（データなしの状態でも問題ない）

REFRESH MATERIALIZED VIEW department_attendance_stats;
