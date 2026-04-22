-- ============================================
-- V12: シフトパターンサマリー（Read Model）
-- ============================================
-- 対応設計書: review-009 指摘 #2 — CQRS Read Model の一貫性確保
--
-- 目的:
-- ShiftPattern 集約の Read Model を新設し、Query 側が Write Model
-- （shift_patterns）を直接参照している現状を解消する。
-- Projector が ShiftPatternDefinedEvent / ShiftPatternDeactivatedEvent /
-- ShiftPatternReactivatedEvent を購読して本テーブルを更新する。
--
-- 非正規化方針:
-- パターンは少数（通常 50 件以下）のため全フィールドをそのまま複製。
-- 将来的な集計カラム（例: 利用従業員数）は必要になった時点で追加する。
-- ============================================

-- === shift_pattern_summaries（シフトパターンサマリー） ===

CREATE TABLE shift_pattern_summaries (
    shift_pattern_id UUID PRIMARY KEY
        REFERENCES shift_patterns(id),
    name VARCHAR(20) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    break_minutes SMALLINT NOT NULL DEFAULT 0,
    is_overnight BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_event_at TIMESTAMPTZ,
    event_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT chk_shift_pattern_summaries_name_len
        CHECK (LENGTH(name) BETWEEN 2 AND 20),
    CONSTRAINT chk_shift_pattern_summaries_break
        CHECK (break_minutes BETWEEN 0 AND 120)
);

-- === インデックス ===

-- is_active フィルタ用（一覧取得の主要条件）
CREATE INDEX idx_shift_pattern_summaries_active
    ON shift_pattern_summaries (is_active);

-- 名前昇順ソート用（一覧取得のデフォルトソート）
CREATE INDEX idx_shift_pattern_summaries_name
    ON shift_pattern_summaries (name);

-- 論理削除除外用（部分インデックス）
CREATE INDEX idx_shift_pattern_summaries_deleted_at
    ON shift_pattern_summaries (deleted_at)
    WHERE deleted_at IS NOT NULL;

-- === 既存データの初期投入 ===
-- 既に shift_patterns に存在するパターンを Read Model に同期する。
-- 以降の変更は Projector がイベント駆動で反映する。

INSERT INTO shift_pattern_summaries (
    shift_pattern_id, name, start_time, end_time,
    break_minutes, is_overnight, is_active,
    last_event_at, event_count,
    created_at, updated_at,
    created_by, updated_by, deleted_at
)
SELECT
    id, name, start_time, end_time,
    break_minutes, is_overnight, is_active,
    NULL, 0,
    created_at, updated_at,
    created_by, updated_by, deleted_at
FROM shift_patterns;
