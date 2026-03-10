-- ============================================
-- V8: 従業員ロールテーブル + シードデータ
-- ============================================
-- 認証・認可基盤（5-1）で使用するロール管理テーブル
-- ロール: EMPLOYEE（一般社員）, MANAGER（管理者）, HR（人事）, ADMIN（システム管理者）
-- ============================================

-- === 従業員ロールテーブル ===
CREATE TABLE employee_roles (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id VARCHAR(36)  NOT NULL REFERENCES employees(employee_id),
    role        VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_employee_role UNIQUE (employee_id, role),
    CONSTRAINT chk_role CHECK (role IN ('EMPLOYEE', 'MANAGER', 'HR', 'ADMIN'))
);

CREATE INDEX idx_employee_roles_employee ON employee_roles(employee_id);

-- === シードデータ ===
-- emp-001 (山田太郎): 営業部マネージャー → EMPLOYEE + MANAGER + ADMIN
INSERT INTO employee_roles (employee_id, role) VALUES
    ('emp-001', 'EMPLOYEE'),
    ('emp-001', 'MANAGER'),
    ('emp-001', 'ADMIN');

-- emp-002 (鈴木花子): 営業部主任 → EMPLOYEE
INSERT INTO employee_roles (employee_id, role) VALUES
    ('emp-002', 'EMPLOYEE');

-- emp-003 (田中一郎): 開発部エンジニア → EMPLOYEE
INSERT INTO employee_roles (employee_id, role) VALUES
    ('emp-003', 'EMPLOYEE');

-- emp-004 (佐藤美咲): 営業部シフト勤務 → EMPLOYEE
INSERT INTO employee_roles (employee_id, role) VALUES
    ('emp-004', 'EMPLOYEE');

-- emp-005 (高橋健二): 人事部 → EMPLOYEE + HR
INSERT INTO employee_roles (employee_id, role) VALUES
    ('emp-005', 'EMPLOYEE'),
    ('emp-005', 'HR');
