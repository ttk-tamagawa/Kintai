-- ============================================
-- V2: 共有テーブル（部署マスタ・従業員マスタ）
-- ============================================
-- 対応設計書: 30_設計/コンテキスト間連携.md
-- ============================================

-- === 部署マスタ ===
CREATE TABLE departments (
    id         VARCHAR(36)  PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    manager_id VARCHAR(36),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- === 従業員マスタ ===
CREATE TABLE employees (
    employee_id     VARCHAR(36)  PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    department_id   VARCHAR(36)  NOT NULL REFERENCES departments(id),
    department_name VARCHAR(100) NOT NULL,
    position        VARCHAR(50),
    manager_id      VARCHAR(36)  REFERENCES employees(employee_id),
    employment_type VARCHAR(20)  NOT NULL DEFAULT 'FULL_TIME',
    hire_date       DATE         NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_employees_department ON employees(department_id);
CREATE INDEX idx_employees_manager ON employees(manager_id);
