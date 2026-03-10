-- ============================================
-- V10: weekly_schedule_summaries.assigned_days の型を SMALLINT → INTEGER に修正
-- ============================================
-- Hibernate のスキーマバリデーションで Java int (INTEGER) と
-- DB SMALLINT の不一致が発生するため、INTEGER に統一する。
-- ============================================

ALTER TABLE weekly_schedule_summaries
    ALTER COLUMN assigned_days SET DATA TYPE INTEGER;
