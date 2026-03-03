-- ============================================
-- V11: employee_id カラムの型を UUID → VARCHAR(36) に統一
-- ============================================
-- employees テーブル（マスタ）の employee_id は VARCHAR(36) だが、
-- 勤怠・シフト関連テーブルでは UUID 型で定義されていたため型不一致が発生。
-- 全テーブルを VARCHAR(36) に統一する。
-- ============================================

-- 1. マテリアライズドビューを先に削除する（依存カラムの型変更のため）
DROP MATERIALIZED VIEW IF EXISTS department_attendance_stats;

-- 2. 勤怠 Write Model
ALTER TABLE attendances
    ALTER COLUMN employee_id TYPE VARCHAR(36) USING employee_id::TEXT;

-- 3. 勤怠 Read Model（日次サマリー）
ALTER TABLE attendance_summaries
    ALTER COLUMN employee_id TYPE VARCHAR(36) USING employee_id::TEXT;

-- 4. 勤怠 Read Model（月次サマリー）
ALTER TABLE monthly_attendance_summaries
    ALTER COLUMN employee_id TYPE VARCHAR(36) USING employee_id::TEXT;

-- 5. シフト Write Model（週次スケジュール）
ALTER TABLE weekly_schedules
    ALTER COLUMN employee_id TYPE VARCHAR(36) USING employee_id::TEXT;

-- 6. シフト Read Model（週次スケジュールサマリー）
ALTER TABLE weekly_schedule_summaries
    ALTER COLUMN employee_id TYPE VARCHAR(36) USING employee_id::TEXT;

-- 7. マテリアライズドビューを再作成する（V5 と同じ定義）
CREATE MATERIALIZED VIEW department_attendance_stats AS
WITH dept_monthly AS (
    SELECT
        m.department_id,
        d.name AS department_name,
        m.year,
        m.month,
        COUNT(DISTINCT m.employee_id) AS total_employees,
        AVG(m.total_work_minutes)::NUMERIC(10,2) AS avg_work_minutes,
        AVG(m.total_overtime_minutes)::NUMERIC(10,2) AS avg_overtime_minutes,
        MAX(m.total_overtime_minutes) AS max_overtime_minutes,
        SUM(m.total_overtime_minutes) AS total_overtime_minutes,
        AVG(m.total_late_night_minutes)::NUMERIC(10,2) AS avg_late_night_minutes,
        COUNT(CASE WHEN m.total_overtime_minutes > 2700 THEN 1 END) AS overtime_alert_count
    FROM monthly_attendance_summaries m
        JOIN departments d ON d.id = m.department_id
    GROUP BY m.department_id, d.name, m.year, m.month
),
dept_daily_stats AS (
    SELECT
        m.department_id,
        m.year,
        m.month,
        COUNT(CASE WHEN s.status = 'NOT_CLOCKED'
                    AND s.work_date < CURRENT_DATE
              THEN 1 END) AS missing_clock_count,
        COUNT(CASE WHEN s.status IN ('CLOCKED_IN', 'CLOCKED_OUT', 'FINALIZED')
              THEN 1 END) * 100.0
            / NULLIF(COUNT(*), 0) AS attendance_rate
    FROM monthly_attendance_summaries m
        JOIN attendance_summaries s
          ON s.employee_id = m.employee_id
         AND EXTRACT(YEAR FROM s.work_date) = m.year
         AND EXTRACT(MONTH FROM s.work_date) = m.month
    GROUP BY m.department_id, m.year, m.month
)
SELECT
    dm.department_id,
    dm.department_name,
    dm.year,
    dm.month,
    dm.total_employees,
    dm.avg_work_minutes,
    dm.avg_overtime_minutes,
    dm.max_overtime_minutes,
    dm.total_overtime_minutes,
    dm.avg_late_night_minutes,
    dm.overtime_alert_count::INTEGER,
    COALESCE(ds.missing_clock_count, 0)::INTEGER AS missing_clock_count,
    ds.attendance_rate::NUMERIC(5,2) AS attendance_rate
FROM dept_monthly dm
    LEFT JOIN dept_daily_stats ds
      ON ds.department_id = dm.department_id
     AND ds.year = dm.year
     AND ds.month = dm.month
WITH NO DATA;

-- 8. マテリアライズドビューのインデックスを再作成する
CREATE UNIQUE INDEX idx_dept_att_stats_pk
    ON department_attendance_stats (department_id, year, month);
CREATE INDEX idx_dept_att_stats_overtime
    ON department_attendance_stats (avg_overtime_minutes DESC);
