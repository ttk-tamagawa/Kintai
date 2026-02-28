package com.example.kintai.shared.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 従業員JPAエンティティ（認証用） — employeesテーブルの読み取り専用マッピング
 *
 * <p>認証処理でメールアドレスから従業員情報を検索するために使用する。
 * attendance パッケージの JPA エンティティとは独立して、
 * 認証に必要な最小限のフィールドのみマッピングする。</p>
 */
@Entity
@Table(name = "employees")
public class EmployeeJpaEntity {

    /** 従業員ID */
    @Id
    @Column(name = "employee_id")
    private String employeeId;

    /** メールアドレス（ログイン識別子） */
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /** 氏名 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 所属部署ID */
    @Column(name = "department_id", nullable = false)
    private String departmentId;

    /** 所属部署名 */
    @Column(name = "department_name", nullable = false)
    private String departmentName;

    /** 有効フラグ */
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    // JPA用デフォルトコンストラクタ
    protected EmployeeJpaEntity() {
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public boolean isActive() {
        return isActive;
    }
}
