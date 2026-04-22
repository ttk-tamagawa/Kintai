-- ============================================
-- V13: 日次勤怠サマリーに休憩中フラグを追加
-- ============================================
-- 対応設計書: review-009 指摘 #3 — getTodayAttendance の Write Model 依存を解消
--
-- 目的:
-- 「現在休憩中か否か」の状態を Read Model（attendance_summaries）で表現する
-- ためのカラムを追加する。これにより Query 側が Write Model の集約
-- （AttendanceRecord + clock_entries）をロードせずに済むようになる。
--
-- 更新契機:
-- Projector が以下のイベントで is_on_break を更新する:
--   - BreakStartedEvent       → true
--   - BreakEndedEvent         → false
--   - ClockedOutEvent         → false（防御的）
--   - AttendanceFinalizedEvent → false（防御的）
--
-- 既存行の初期値:
-- DEFAULT false を指定しているため、既存の全行で is_on_break = false になる。
-- 運用中に実際に休憩中だった従業員がいても、データ破壊はなく、
-- 次の break/clockout イベントで自動的に正しい状態に復旧する。
-- ============================================

ALTER TABLE attendance_summaries
    ADD COLUMN is_on_break BOOLEAN NOT NULL DEFAULT false;
