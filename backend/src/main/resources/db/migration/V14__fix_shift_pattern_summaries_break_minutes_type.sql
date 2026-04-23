-- ============================================
-- V14: shift_pattern_summaries.break_minutes SMALLINT → INTEGER 型修正
-- ============================================
-- V9 で shift_patterns.break_minutes を INTEGER に修正したが、
-- V12 で Read Model (shift_pattern_summaries) を新設した際に同じ
-- 問題が再発。Hibernate のスキーマバリデーションで Java int (INTEGER)
-- と DB SMALLINT の不一致により bootRun が起動不能になるため、
-- Write Model と同じく INTEGER に統一する。
-- ============================================

ALTER TABLE shift_pattern_summaries
    ALTER COLUMN break_minutes SET DATA TYPE INTEGER;
