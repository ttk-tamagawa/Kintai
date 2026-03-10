package com.example.kintai.shared.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 従業員ロールJPAエンティティ — employee_rolesテーブルのマッピング
 *
 * <p>従業員に割り当てられたロール（EMPLOYEE, MANAGER, HR, ADMIN）を保持する。
 * 1従業員に複数ロールを割り当て可能。</p>
 */
@Entity
@Table(name = "employee_roles")
public class EmployeeRoleJpaEntity {

    /** 自動採番ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 従業員ID（外部キー） */
    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    /** ロール名 */
    @Column(name = "role", nullable = false)
    private String role;

    // JPA用デフォルトコンストラクタ
    protected EmployeeRoleJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getRole() {
        return role;
    }
}
