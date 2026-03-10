-- ============================================
-- V9: SMALLINT → INTEGER 型修正
-- ============================================
-- Hibernate のスキーマバリデーションで Java int (INTEGER) と
-- DB SMALLINT の不一致が発生するため、INTEGER に統一する。
-- 対象: shift_patterns.break_minutes
-- ============================================

ALTER TABLE shift_patterns
    ALTER COLUMN break_minutes SET DATA TYPE INTEGER;
