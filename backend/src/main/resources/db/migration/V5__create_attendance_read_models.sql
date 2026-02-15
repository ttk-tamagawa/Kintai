-- ============================================
-- V5: 勤怠記録テーブル（Read Model）
-- ============================================
-- 対応設計書: 30_設計/データベース/勤怠記録.md
-- ============================================

-- === attendance_summaries（日次勤怠サマリー） ===

CREATE TABLE attendance_summaries (
    attendance_id UUID PRIMARY KEY
        REFERENCES attendances(id),
    employee_id UUID NOT NULL,
    work_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_CLOCKED',
    clock_in_time TIMESTAMPTZ,
    clock_out_time TIMESTAMPTZ,
    scheduled_minutes INTEGER NOT NULL DEFAULT 0,
    actual_minutes INTEGER NOT NULL DEFAULT 0,
    break_minutes INTEGER NOT NULL DEFAULT 0,
    net_work_minutes INTEGER NOT NULL DEFAULT 0,
    regular_overtime_minutes INTEGER NOT NULL DEFAULT 0,
    late_night_minutes INTEGER NOT NULL DEFAULT 0,
    holiday_minutes INTEGER NOT NULL DEFAULT 0,
    total_overtime_minutes INTEGER NOT NULL DEFAULT 0,
    last_event_at TIMESTAMPTZ,
    event_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT chk_att_summaries_clock
        CHECK (clock_out_time IS NULL
            OR clock_in_time IS NULL
            OR clock_in_time < clock_out_time),
    CONSTRAINT chk_att_summaries_status
        CHECK (status IN (
            'NOT_CLOCKED', 'CLOCKED_IN',
            'CLOCKED_OUT', 'FINALIZED'
        ))
);

CREATE INDEX idx_att_summaries_employee_date
    ON attendance_summaries (employee_id, work_date DESC);
CREATE INDEX idx_att_summaries_status
    ON attendance_summaries (status);
CREATE INDEX idx_att_summaries_overtime
    ON attendance_summaries (total_overtime_minutes DESC);
CREATE INDEX idx_att_summaries_deleted_at
    ON attendance_summaries (deleted_at)
    WHERE deleted_at IS NOT NULL;

-- === monthly_attendance_summaries（月次勤怠サマリー） ===

CREATE TABLE monthly_attendance_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    employee_name VARCHAR(255) NOT NULL,
    department_id VARCHAR(36) NOT NULL,
    year SMALLINT NOT NULL,
    month SMALLINT NOT NULL,
    total_work_days INTEGER NOT NULL DEFAULT 0,
    total_work_minutes INTEGER NOT NULL DEFAULT 0,
    total_overtime_minutes INTEGER NOT NULL DEFAULT 0,
    total_late_night_minutes INTEGER NOT NULL DEFAULT 0,
    total_holiday_minutes INTEGER NOT NULL DEFAULT 0,
    total_break_minutes INTEGER NOT NULL DEFAULT 0,
    paid_leave_used NUMERIC(5,1) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,

    CONSTRAINT uk_monthly_att_summaries
        UNIQUE (employee_id, year, month),
    CONSTRAINT chk_monthly_att_month
        CHECK (month BETWEEN 1 AND 12)
);

CREATE INDEX idx_monthly_att_dept_month
    ON monthly_attendance_summaries (department_id, year, month);
CREATE INDEX idx_monthly_att_employee
    ON monthly_attendance_summaries (employee_name);
CREATE INDEX idx_monthly_att_overtime
    ON monthly_attendance_summaries (total_overtime_minutes DESC);

-- === department_attendance_stats（部門別勤怠ダッシュボード） ===

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

CREATE UNIQUE INDEX idx_dept_att_stats_pk
    ON department_attendance_stats (department_id, year, month);
CREATE INDEX idx_dept_att_stats_overtime
    ON department_attendance_stats (avg_overtime_minutes DESC);
