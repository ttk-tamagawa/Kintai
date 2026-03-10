-- ============================================
-- V4: 勤怠記録テーブル（Write Model）
-- ============================================
-- 対応設計書: 30_設計/データベース/勤怠記録.md
-- ============================================

-- === attendances（集約ルート） ===

CREATE TABLE attendances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    work_date DATE NOT NULL,
    shift_pattern_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_CLOCKED',
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT uk_attendances_employee_date
        UNIQUE (employee_id, work_date),
    CONSTRAINT chk_attendances_status
        CHECK (status IN (
            'NOT_CLOCKED', 'CLOCKED_IN',
            'CLOCKED_OUT', 'FINALIZED'
        ))
);

CREATE INDEX idx_attendances_employee_date
    ON attendances (employee_id, work_date DESC);
CREATE INDEX idx_attendances_status
    ON attendances (status);
CREATE INDEX idx_attendances_deleted_at
    ON attendances (deleted_at)
    WHERE deleted_at IS NOT NULL;

-- === attendance_events（イベントログ） ===

CREATE TABLE attendance_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attendance_id UUID NOT NULL
        REFERENCES attendances(id),
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recorded_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,

    CONSTRAINT chk_attendance_events_type
        CHECK (event_type IN (
            'CLOCKED_IN', 'CLOCKED_OUT',
            'BREAK_STARTED', 'BREAK_ENDED',
            'DURATION_CALCULATED', 'CLOCK_CORRECTED',
            'MANUAL_REGISTERED', 'FINALIZED'
        ))
);

CREATE INDEX idx_att_events_lookup
    ON attendance_events (attendance_id, occurred_at DESC);
CREATE INDEX idx_att_events_type
    ON attendance_events (event_type);
CREATE INDEX idx_att_events_occurred_at
    ON attendance_events (occurred_at);

-- === clock_entries（打刻ログ） ===

CREATE TABLE clock_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attendance_id UUID NOT NULL
        REFERENCES attendances(id),
    type VARCHAR(20) NOT NULL,
    time TIMESTAMPTZ NOT NULL,
    source VARCHAR(20) NOT NULL,
    correction_id UUID
        REFERENCES clock_entries(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,

    CONSTRAINT chk_clock_entries_type
        CHECK (type IN (
            'CLOCK_IN', 'CLOCK_OUT',
            'BREAK_START', 'BREAK_END'
        )),
    CONSTRAINT chk_clock_entries_source
        CHECK (source IN (
            'WEB', 'MOBILE', 'MANUAL', 'CORRECTION'
        ))
);

CREATE INDEX idx_clock_entries_attendance
    ON clock_entries (attendance_id, time ASC);
CREATE INDEX idx_clock_entries_type
    ON clock_entries (type);
