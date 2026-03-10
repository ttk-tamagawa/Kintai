-- ============================================
-- V3: 開発用シードデータ
-- ============================================
-- 部署マスタ（3部署）+ 従業員マスタ（5名）
-- ============================================

-- === 部署マスタ ===
INSERT INTO departments (id, name) VALUES
    ('dept-001', '営業部'),
    ('dept-002', '開発部'),
    ('dept-003', '人事部');

-- === 従業員マスタ ===
-- 1. 管理者（営業部マネージャー）
INSERT INTO employees (employee_id, email, name, department_id, department_name, position, manager_id, employment_type, hire_date)
VALUES ('emp-001', 'yamada@example.com', '山田 太郎', 'dept-001', '営業部', 'マネージャー', NULL, 'FULL_TIME', '2020-04-01');

-- 2. 一般社員（営業部、固定勤務）
INSERT INTO employees (employee_id, email, name, department_id, department_name, position, manager_id, employment_type, hire_date)
VALUES ('emp-002', 'suzuki@example.com', '鈴木 花子', 'dept-001', '営業部', '主任', 'emp-001', 'FULL_TIME', '2021-04-01');

-- 3. 一般社員（開発部、固定勤務）
INSERT INTO employees (employee_id, email, name, department_id, department_name, position, manager_id, employment_type, hire_date)
VALUES ('emp-003', 'tanaka@example.com', '田中 一郎', 'dept-002', '開発部', 'エンジニア', NULL, 'FULL_TIME', '2022-04-01');

-- 4. シフト勤務社員（営業部）
INSERT INTO employees (employee_id, email, name, department_id, department_name, position, manager_id, employment_type, hire_date)
VALUES ('emp-004', 'sato@example.com', '佐藤 美咲', 'dept-001', '営業部', NULL, 'emp-001', 'SHIFT', '2023-04-01');

-- 5. 人事部社員（HR権限用）
INSERT INTO employees (employee_id, email, name, department_id, department_name, position, manager_id, employment_type, hire_date)
VALUES ('emp-005', 'takahashi@example.com', '高橋 健二', 'dept-003', '人事部', '人事担当', NULL, 'FULL_TIME', '2019-04-01');

-- 部署にマネージャーを設定
UPDATE departments SET manager_id = 'emp-001' WHERE id = 'dept-001';
UPDATE departments SET manager_id = 'emp-003' WHERE id = 'dept-002';
UPDATE departments SET manager_id = 'emp-005' WHERE id = 'dept-003';
