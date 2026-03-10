-- ============================================
-- V6: シフトテーブル（Write Model + Read Model）
-- ============================================
-- 対応設計書: 30_設計/データベース/シフト.md
-- ============================================

-- === Write Model ===

-- === shift_patterns（シフトパターン） ===

CREATE TABLE shift_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(20) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    break_minutes SMALLINT NOT NULL DEFAULT 0,
    is_overnight BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT uk_shift_patterns_name
        UNIQUE (name),
    CONSTRAINT chk_shift_patterns_name_len
        CHECK (LENGTH(name) BETWEEN 2 AND 20),
    CONSTRAINT chk_shift_patterns_break
        CHECK (break_minutes BETWEEN 0 AND 120)
);

CREATE INDEX idx_shift_patterns_active
    ON shift_patterns (is_active);
CREATE INDEX idx_shift_patterns_deleted_at
    ON shift_patterns (deleted_at)
    WHERE deleted_at IS NOT NULL;

-- === weekly_schedules（週次スケジュール） ===

CREATE TABLE weekly_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    week_start_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    monday_pattern_id UUID
        REFERENCES shift_patterns(id),
    tuesday_pattern_id UUID
        REFERENCES shift_patterns(id),
    wednesday_pattern_id UUID
        REFERENCES shift_patterns(id),
    thursday_pattern_id UUID
        REFERENCES shift_patterns(id),
    friday_pattern_id UUID
        REFERENCES shift_patterns(id),
    saturday_pattern_id UUID
        REFERENCES shift_patterns(id),
    sunday_pattern_id UUID
        REFERENCES shift_patterns(id),
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT uk_weekly_schedules_emp_week
        UNIQUE (employee_id, week_start_date),
    CONSTRAINT chk_weekly_schedules_status
        CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT chk_weekly_schedules_dow
        CHECK (EXTRACT(ISODOW FROM week_start_date) = 1),
    CONSTRAINT chk_weekly_schedules_assigned
        CHECK (
            monday_pattern_id IS NOT NULL
            OR tuesday_pattern_id IS NOT NULL
            OR wednesday_pattern_id IS NOT NULL
            OR thursday_pattern_id IS NOT NULL
            OR friday_pattern_id IS NOT NULL
            OR saturday_pattern_id IS NOT NULL
            OR sunday_pattern_id IS NOT NULL
        )
);

CREATE INDEX idx_weekly_schedules_emp_week
    ON weekly_schedules (employee_id, week_start_date);
CREATE INDEX idx_weekly_schedules_week_status
    ON weekly_schedules (week_start_date, status);
CREATE INDEX idx_weekly_schedules_deleted_at
    ON weekly_schedules (deleted_at)
    WHERE deleted_at IS NOT NULL;

-- === weekly_schedule_events（スケジュールイベント） ===

CREATE TABLE weekly_schedule_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    weekly_schedule_id UUID NOT NULL
        REFERENCES weekly_schedules(id),
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recorded_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,

    CONSTRAINT chk_ws_events_type
        CHECK (event_type IN (
            'ASSIGNED', 'CHANGED',
            'PUBLISHED', 'UNPUBLISHED'
        ))
);

CREATE INDEX idx_ws_events_lookup
    ON weekly_schedule_events (weekly_schedule_id, occurred_at DESC);
CREATE INDEX idx_ws_events_type
    ON weekly_schedule_events (event_type);
CREATE INDEX idx_ws_events_occurred_at
    ON weekly_schedule_events (occurred_at);

-- === Read Model ===

-- === weekly_schedule_summaries（週次スケジュールサマリー） ===

CREATE TABLE weekly_schedule_summaries (
    weekly_schedule_id UUID PRIMARY KEY
        REFERENCES weekly_schedules(id),
    employee_id UUID NOT NULL,
    week_start_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    monday_pattern_id UUID,
    monday_pattern_name VARCHAR(20),
    tuesday_pattern_id UUID,
    tuesday_pattern_name VARCHAR(20),
    wednesday_pattern_id UUID,
    wednesday_pattern_name VARCHAR(20),
    thursday_pattern_id UUID,
    thursday_pattern_name VARCHAR(20),
    friday_pattern_id UUID,
    friday_pattern_name VARCHAR(20),
    saturday_pattern_id UUID,
    saturday_pattern_name VARCHAR(20),
    sunday_pattern_id UUID,
    sunday_pattern_name VARCHAR(20),
    assigned_days SMALLINT NOT NULL DEFAULT 0,
    last_event_at TIMESTAMPTZ,
    event_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT chk_ws_summaries_status
        CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE INDEX idx_ws_summaries_emp_week
    ON weekly_schedule_summaries (employee_id, week_start_date);
CREATE INDEX idx_ws_summaries_week_status
    ON weekly_schedule_summaries (week_start_date, status);
CREATE INDEX idx_ws_summaries_deleted_at
    ON weekly_schedule_summaries (deleted_at)
    WHERE deleted_at IS NOT NULL;
