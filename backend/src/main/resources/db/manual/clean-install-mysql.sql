-- Clean-install schema for the current Payroll SaaS JPA model.
-- Target: a newly-created, empty MySQL 8.0.39 database named payroll_saas.
-- This is not an upgrade script. Do not run against a database containing tables.
-- Do not run phase4-attendance-mysql.sql, phase4-1-employee-user-link-mysql.sql,
-- phase5-leave-management-mysql.sql, or phase6-salary-structure-mysql.sql after
-- this file; it includes the current JPA final-state tables and fields instead.
--
-- Review backend/DATABASE_INSTALLATION.md before installation. Component values
-- use DECIMAL(14,4) for percentage rates and fixed values; fixed values are
-- validated to scale 2 by the application. Monetary totals use DECIMAL(14,2).

USE payroll_saas;
SET NAMES utf8mb4;

CREATE TABLE companies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    tenant_id BIGINT NOT NULL,
    subscription_plan VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_companies_code (code),
    UNIQUE KEY uk_company_id_tenant (id, tenant_id),
    KEY idx_companies_tenant (tenant_id),
    CONSTRAINT chk_companies_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    tenant_id BIGINT NULL,
    company_id BIGINT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
    reset_token VARCHAR(255) NULL,
    reset_token_expiry DATETIME(6) NULL,
    last_login DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_id_company_tenant (id, company_id, tenant_id),
    KEY idx_users_company_tenant (company_id, tenant_id),
    KEY idx_users_role (role),
    CONSTRAINT fk_users_company_tenant
        FOREIGN KEY (company_id, tenant_id)
        REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_users_role
        CHECK (role IN ('SUPER_ADMIN', 'COMPANY_ADMIN', 'HR_ADMIN', 'EMPLOYEE')),
    CONSTRAINT chk_users_company_scope
        CHECK (
            (company_id IS NULL AND role = 'SUPER_ADMIN'
                AND tenant_id IS NOT NULL AND tenant_id = 0)
            OR
            (company_id IS NOT NULL AND tenant_id IS NOT NULL AND role <> 'SUPER_ADMIN')
        )
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE departments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_department_company_name (company_id, name),
    UNIQUE KEY uk_departments_id_company_tenant (id, company_id, tenant_id),
    KEY idx_departments_company_tenant (company_id, tenant_id),
    CONSTRAINT fk_departments_company_tenant
        FOREIGN KEY (company_id, tenant_id)
        REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE designations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_designation_company_name (company_id, name),
    UNIQUE KEY uk_designations_id_company_tenant (id, company_id, tenant_id),
    KEY idx_designations_company_tenant (company_id, tenant_id),
    CONSTRAINT fk_designations_company_tenant
        FOREIGN KEY (company_id, tenant_id)
        REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE employees (
    id BIGINT NOT NULL AUTO_INCREMENT,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    employee_number VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    date_of_birth DATE NULL,
    gender VARCHAR(32) NULL,
    job_title VARCHAR(120) NOT NULL,
    reporting_manager_id BIGINT NULL,
    emergency_contact_name VARCHAR(120) NULL,
    emergency_contact_phone VARCHAR(40) NULL,
    emergency_contact_relationship VARCHAR(80) NULL,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    department_id BIGINT NULL,
    designation_id BIGINT NULL,
    date_of_joining DATE NOT NULL,
    employment_status VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATE NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_company_number (company_id, employee_number),
    UNIQUE KEY uk_employee_company_email (company_id, email),
    UNIQUE KEY uk_employee_user (user_id),
    UNIQUE KEY uk_employee_id_company_tenant (id, company_id, tenant_id),
    KEY idx_employees_tenant (tenant_id),
    KEY idx_employees_company_tenant (company_id, tenant_id),
    KEY idx_employees_department_scope (department_id, company_id, tenant_id),
    KEY idx_employees_designation_scope (designation_id, company_id, tenant_id),
    KEY idx_employees_reporting_manager_scope (reporting_manager_id, company_id, tenant_id),
    KEY idx_employees_user_scope (user_id, company_id, tenant_id),
    CONSTRAINT fk_employees_company_tenant
        FOREIGN KEY (company_id, tenant_id)
        REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_employee_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_employee_user_scope
        FOREIGN KEY (user_id, company_id, tenant_id)
        REFERENCES users (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_employees_department
        FOREIGN KEY (department_id) REFERENCES departments (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_employees_department_scope
        FOREIGN KEY (department_id, company_id, tenant_id)
        REFERENCES departments (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_employees_designation
        FOREIGN KEY (designation_id) REFERENCES designations (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_employees_designation_scope
        FOREIGN KEY (designation_id, company_id, tenant_id)
        REFERENCES designations (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_employees_reporting_manager_scope
        FOREIGN KEY (reporting_manager_id, company_id, tenant_id)
        REFERENCES employees (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_tokens_token (token),
    KEY idx_password_reset_tokens_user (user_id),
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE token_blacklist (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_token_blacklist_token (token)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    action VARCHAR(255) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    actor_role VARCHAR(255) NULL,
    tenant_id BIGINT NULL,
    company_id BIGINT NULL,
    details TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_logs_company_tenant_created (company_id, tenant_id, created_at),
    KEY idx_audit_logs_tenant_created (tenant_id, created_at),
    CONSTRAINT fk_audit_logs_company_tenant
        FOREIGN KEY (company_id, tenant_id)
        REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_audit_logs_company_scope
        CHECK (company_id IS NULL OR tenant_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_types (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    code VARCHAR(24) NOT NULL,
    name VARCHAR(120) NOT NULL,
    paid BOOLEAN NOT NULL,
    annual_entitlement DECIMAL(7,2) NOT NULL,
    carry_forward_limit DECIMAL(7,2) NOT NULL,
    max_consecutive_days INT NOT NULL,
    supporting_document_required BOOLEAN NOT NULL DEFAULT FALSE,
    allow_past_applications BOOLEAN NOT NULL DEFAULT FALSE,
    allow_half_day BOOLEAN NOT NULL DEFAULT FALSE,
    allow_negative_balance BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_type_company_code (company_id, code),
    UNIQUE KEY uk_leave_type_id_company_tenant (id, company_id, tenant_id),
    KEY idx_leave_type_company (company_id, tenant_id, active),
    KEY idx_leave_type_company_name (company_id, tenant_id, name),
    KEY idx_leave_type_company_active_name (company_id, tenant_id, active, name),
    CONSTRAINT fk_leave_type_company_tenant
        FOREIGN KEY (company_id, tenant_id)
        REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_leave_type_amounts
        CHECK (annual_entitlement >= 0 AND carry_forward_limit >= 0 AND max_consecutive_days >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE company_holidays (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    `date` DATE NOT NULL,
    description VARCHAR(500) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_holiday_date (company_id, `date`),
    KEY idx_company_holiday_range (company_id, tenant_id, `date`, active),
    CONSTRAINT fk_company_holiday_company_tenant
        FOREIGN KEY (company_id, tenant_id)
        REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    leave_type_id BIGINT NULL,
    leave_type VARCHAR(120) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    days DECIMAL(7,2) NOT NULL,
    working_dates TEXT NULL,
    leave_paid BOOLEAN NOT NULL,
    half_day BOOLEAN NOT NULL,
    half_day_period VARCHAR(16) NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    review_comment VARCHAR(1000) NULL,
    cancellation_comment VARCHAR(1000) NULL,
    supporting_document_key VARCHAR(36) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_request_id_company_tenant (id, company_id, tenant_id),
    UNIQUE KEY uk_leave_request_id_employee_company_tenant
        (id, employee_id, company_id, tenant_id),
    KEY idx_leave_request_company_status (company_id, tenant_id, status, start_date),
    KEY idx_leave_request_employee_dates (employee_id, start_date, end_date),
    KEY idx_leave_request_employee_history (employee_id, company_id, tenant_id, created_at),
    KEY idx_leave_request_employee_type_status
        (employee_id, company_id, tenant_id, leave_type_id, status),
    KEY idx_leave_request_employee_status_dates
        (employee_id, company_id, tenant_id, status, start_date, end_date),
    KEY idx_leave_request_type_scope (leave_type_id, company_id, tenant_id),
    KEY idx_leave_request_reviewer_scope (reviewed_by, company_id, tenant_id),
    CONSTRAINT fk_leave_request_employee_tenant
        FOREIGN KEY (employee_id, company_id, tenant_id)
        REFERENCES employees (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_leave_request_type_scope
        FOREIGN KEY (leave_type_id, company_id, tenant_id)
        REFERENCES leave_types (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_leave_request_reviewer_scope
        FOREIGN KEY (reviewed_by, company_id, tenant_id)
        REFERENCES users (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_leave_request_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_leave_request_days CHECK (days > 0),
    CONSTRAINT chk_leave_request_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLATION_PENDING', 'CANCELLED')),
    CONSTRAINT chk_leave_request_half_day
        CHECK (half_day_period IS NULL OR half_day_period IN ('MORNING', 'AFTERNOON'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE attendance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    `date` DATE NOT NULL,
    status VARCHAR(24) NOT NULL,
    check_in_time DATETIME(6) NULL,
    check_in_instant TIMESTAMP(6) NULL,
    check_out_time DATETIME(6) NULL,
    check_out_instant TIMESTAMP(6) NULL,
    time_zone_id VARCHAR(64) NOT NULL DEFAULT 'UTC',
    total_working_minutes INT NOT NULL DEFAULT 0,
    late_arrival BOOLEAN NOT NULL DEFAULT FALSE,
    early_departure BOOLEAN NOT NULL DEFAULT FALSE,
    overtime_minutes INT NOT NULL DEFAULT 0,
    notes VARCHAR(1000) NULL,
    approval_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUIRED',
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    approved_by BIGINT NULL,
    leave_request_id BIGINT NULL,
    leave_day_fraction DECIMAL(3,2) NULL,
    leave_paid BOOLEAN NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance_employee_date (employee_id, `date`),
    KEY idx_attendance_employee_scope (employee_id, company_id, tenant_id),
    KEY idx_attendance_company_date (company_id, tenant_id, `date`),
    KEY idx_attendance_leave_request_scope (leave_request_id, employee_id, company_id, tenant_id),
    KEY idx_attendance_created_by_scope (created_by, company_id, tenant_id),
    KEY idx_attendance_updated_by_scope (updated_by, company_id, tenant_id),
    KEY idx_attendance_approved_by_scope (approved_by, company_id, tenant_id),
    CONSTRAINT fk_attendance_employee_tenant
        FOREIGN KEY (employee_id, company_id, tenant_id)
        REFERENCES employees (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_leave_request_scope
        FOREIGN KEY (leave_request_id, employee_id, company_id, tenant_id)
        REFERENCES leave_requests (id, employee_id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_created_by
        FOREIGN KEY (created_by, company_id, tenant_id)
        REFERENCES users (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_updated_by
        FOREIGN KEY (updated_by, company_id, tenant_id)
        REFERENCES users (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_approved_by
        FOREIGN KEY (approved_by, company_id, tenant_id)
        REFERENCES users (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_attendance_status
        CHECK (status IN ('PRESENT', 'ABSENT', 'HALF_DAY', 'LATE', 'ON_LEAVE', 'HOLIDAY', 'WEEK_OFF')),
    CONSTRAINT chk_attendance_approval_status
        CHECK (approval_status IN ('NOT_REQUIRED', 'PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_attendance_minutes
        CHECK (total_working_minutes >= 0 AND overtime_minutes >= 0),
    CONSTRAINT chk_attendance_leave_fraction
        CHECK (leave_day_fraction IS NULL OR leave_day_fraction BETWEEN 0 AND 1)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE attendance_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    work_start_time TIME NOT NULL,
    work_end_time TIME NOT NULL,
    required_working_minutes INT NOT NULL,
    grace_period_minutes INT NOT NULL,
    half_day_threshold_minutes INT NOT NULL,
    overtime_threshold_minutes INT NOT NULL,
    weekly_holidays VARCHAR(80) NOT NULL,
    time_zone_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance_rules_company (company_id),
    KEY idx_attendance_rules_company_tenant (company_id, tenant_id),
    CONSTRAINT fk_attendance_rules_company_tenant
        FOREIGN KEY (company_id, tenant_id)
        REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_attendance_rules_minutes
        CHECK (
            required_working_minutes >= 0 AND grace_period_minutes >= 0
            AND half_day_threshold_minutes >= 0 AND overtime_threshold_minutes >= 0
        )
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_balances (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    leave_type_id BIGINT NOT NULL,
    leave_year INT NOT NULL,
    opening_balance DECIMAL(7,2) NOT NULL DEFAULT 0,
    annual_entitlement DECIMAL(7,2) NOT NULL DEFAULT 0,
    carry_forward DECIMAL(7,2) NOT NULL DEFAULT 0,
    accrued DECIMAL(7,2) NOT NULL DEFAULT 0,
    paid_used DECIMAL(7,2) NOT NULL DEFAULT 0,
    lop_used DECIMAL(7,2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_balance_employee_type_year (employee_id, leave_type_id, leave_year),
    UNIQUE KEY uk_leave_balance_id_company_tenant (id, company_id, tenant_id),
    UNIQUE KEY uk_leave_balance_id_employee_company_tenant
        (id, employee_id, company_id, tenant_id),
    KEY idx_leave_balance_company (company_id, tenant_id, leave_year),
    KEY idx_leave_balance_employee_scope (employee_id, company_id, tenant_id, leave_year),
    KEY idx_leave_balance_employee_type_year
        (employee_id, company_id, tenant_id, leave_type_id, leave_year),
    KEY idx_leave_balance_type_scope (leave_type_id, company_id, tenant_id),
    CONSTRAINT fk_leave_balance_employee_tenant
        FOREIGN KEY (employee_id, company_id, tenant_id)
        REFERENCES employees (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_leave_balance_type_scope
        FOREIGN KEY (leave_type_id, company_id, tenant_id)
        REFERENCES leave_types (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_balance_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    leave_balance_id BIGINT NOT NULL,
    leave_request_id BIGINT NULL,
    employee_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    paid_delta DECIMAL(7,2) NOT NULL DEFAULT 0,
    lop_delta DECIMAL(7,2) NOT NULL DEFAULT 0,
    actor_user_id BIGINT NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_balance_request_action
        (leave_request_id, action, leave_balance_id),
    KEY idx_leave_ledger_balance (leave_balance_id, created_at),
    KEY idx_leave_ledger_balance_scope
        (leave_balance_id, employee_id, company_id, tenant_id),
    KEY idx_leave_ledger_request_scope
        (leave_request_id, employee_id, company_id, tenant_id),
    KEY idx_leave_ledger_request (leave_request_id),
    KEY idx_leave_ledger_actor_scope (actor_user_id, company_id, tenant_id),
    CONSTRAINT fk_leave_ledger_balance
        FOREIGN KEY (leave_balance_id, employee_id, company_id, tenant_id)
        REFERENCES leave_balances (id, employee_id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_leave_ledger_request
        FOREIGN KEY (leave_request_id, employee_id, company_id, tenant_id)
        REFERENCES leave_requests (id, employee_id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_leave_ledger_actor
        FOREIGN KEY (actor_user_id, company_id, tenant_id)
        REFERENCES users (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_leave_ledger_action
        CHECK (action IN ('ALLOCATION', 'APPROVAL', 'CANCELLATION_REVERSAL'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE salary_component_definitions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(16) NOT NULL,
    calculation_method VARCHAR(20) NOT NULL,
    taxable BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_salary_component_company_code (company_id, code),
    UNIQUE KEY uk_salary_component_id_company_tenant (id, company_id, tenant_id),
    KEY idx_salary_component_company_active (company_id, tenant_id, active),
    KEY idx_salary_component_company_name (company_id, tenant_id, name),
    KEY idx_salary_component_company_active_name (company_id, tenant_id, active, name),
    CONSTRAINT fk_salary_component_company_tenant
        FOREIGN KEY (company_id, tenant_id)
        REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_salary_component_type CHECK (type IN ('EARNING', 'DEDUCTION')),
    CONSTRAINT chk_salary_component_method
        CHECK (calculation_method IN ('FIXED_AMOUNT', 'PERCENTAGE')),
    CONSTRAINT chk_salary_component_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE salary_structures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_salary_structure_company_code (company_id, code),
    UNIQUE KEY uk_salary_structure_id_company_tenant (id, company_id, tenant_id),
    KEY idx_salary_structure_company_active (company_id, tenant_id, active),
    KEY idx_salary_structure_company_name (company_id, tenant_id, name),
    CONSTRAINT fk_salary_structure_company_tenant
        FOREIGN KEY (company_id, tenant_id)
        REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_salary_structure_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE salary_structure_components (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    salary_structure_id BIGINT NOT NULL,
    component_id BIGINT NOT NULL,
    percentage_base_component_id BIGINT NULL,
    component_value DECIMAL(14,4) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_salary_structure_component (salary_structure_id, component_id),
    UNIQUE KEY uk_salary_structure_component_id_company_tenant (id, company_id, tenant_id),
    KEY idx_salary_structure_component_scope (salary_structure_id, company_id, tenant_id),
    KEY idx_salary_structure_component_definition_scope (component_id, company_id, tenant_id),
    KEY idx_salary_structure_percentage_base_scope
        (percentage_base_component_id, company_id, tenant_id),
    CONSTRAINT fk_salary_structure_component_structure
        FOREIGN KEY (salary_structure_id, company_id, tenant_id)
        REFERENCES salary_structures (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_salary_structure_component_definition
        FOREIGN KEY (component_id, company_id, tenant_id)
        REFERENCES salary_component_definitions (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_salary_structure_percentage_base
        FOREIGN KEY (percentage_base_component_id, company_id, tenant_id)
        REFERENCES salary_component_definitions (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_salary_structure_component_value CHECK (component_value >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE salary_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    salary_structure_id BIGINT NOT NULL,
    structure_code_snapshot VARCHAR(32) NOT NULL,
    structure_name_snapshot VARCHAR(120) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    gross_earnings DECIMAL(14,2) NOT NULL,
    total_deductions DECIMAL(14,2) NOT NULL,
    illustrative_net DECIMAL(14,2) NOT NULL,
    assigned_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_salary_assignment_id_company_tenant (id, company_id, tenant_id),
    KEY idx_salary_assignment_employee_period
        (employee_id, company_id, tenant_id, active, effective_from, effective_to),
    KEY idx_salary_assignment_employee_history
        (employee_id, company_id, tenant_id, effective_from, id),
    KEY idx_salary_assignment_company_period (company_id, tenant_id, active, effective_from),
    KEY idx_salary_assignment_structure_scope (salary_structure_id, company_id, tenant_id),
    KEY idx_salary_assignment_actor_scope (assigned_by, company_id, tenant_id),
    CONSTRAINT fk_salary_assignment_employee_scope
        FOREIGN KEY (employee_id, company_id, tenant_id)
        REFERENCES employees (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_salary_assignment_structure_scope
        FOREIGN KEY (salary_structure_id, company_id, tenant_id)
        REFERENCES salary_structures (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_salary_assignment_actor_scope
        FOREIGN KEY (assigned_by, company_id, tenant_id)
        REFERENCES users (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_salary_assignment_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_salary_assignment_amounts
        CHECK (gross_earnings >= 0 AND total_deductions >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE salary_assignment_components (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    salary_assignment_id BIGINT NOT NULL,
    component_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    component_type VARCHAR(16) NOT NULL,
    calculation_method VARCHAR(20) NOT NULL,
    component_value DECIMAL(14,4) NOT NULL,
    percentage_base_code VARCHAR(32) NULL,
    taxable BOOLEAN NOT NULL,
    calculated_amount DECIMAL(14,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_salary_assignment_component (salary_assignment_id, component_id),
    KEY idx_salary_assignment_component_scope (salary_assignment_id, company_id, tenant_id),
    KEY idx_salary_assignment_component_definition_scope (component_id, company_id, tenant_id),
    CONSTRAINT fk_salary_assignment_component_assignment
        FOREIGN KEY (salary_assignment_id, company_id, tenant_id)
        REFERENCES salary_assignments (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_salary_assignment_component_definition
        FOREIGN KEY (component_id, company_id, tenant_id)
        REFERENCES salary_component_definitions (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_salary_assignment_component_type
        CHECK (component_type IN ('EARNING', 'DEDUCTION')),
    CONSTRAINT chk_salary_assignment_component_method
        CHECK (calculation_method IN ('FIXED_AMOUNT', 'PERCENTAGE')),
    CONSTRAINT chk_salary_assignment_component_amount
        CHECK (component_value >= 0 AND calculated_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE salary_components (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    effective_from DATE NOT NULL,
    PRIMARY KEY (id),
    KEY idx_salary_components_employee_scope (employee_id, company_id, tenant_id),
    CONSTRAINT fk_salary_components_employee_scope
        FOREIGN KEY (employee_id, company_id, tenant_id)
        REFERENCES employees (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payrolls (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    pay_period VARCHAR(255) NOT NULL,
    total_working_days DECIMAL(7,2) NULL,
    paid_days DECIMAL(7,2) NULL,
    unpaid_days DECIMAL(7,2) NULL,
    gross_salary DECIMAL(14,2) NOT NULL,
    deductions DECIMAL(14,2) NOT NULL,
    net_salary DECIMAL(14,2) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    processed_on DATE NULL,
    component_breakdown_json TEXT NULL,
    payslip_number VARCHAR(42) NULL,
    company_name_snapshot VARCHAR(255) NULL,
    company_address_snapshot VARCHAR(255) NULL,
    employee_name_snapshot VARCHAR(511) NULL,
    employee_number_snapshot VARCHAR(255) NULL,
    department_name_snapshot VARCHAR(255) NULL,
    designation_name_snapshot VARCHAR(255) NULL,
    PRIMARY KEY (id),
    KEY idx_payrolls_tenant_period (tenant_id, pay_period),
    KEY idx_payrolls_company_tenant (company_id, tenant_id),
    KEY idx_payrolls_company_period (company_id, tenant_id, pay_period),
    UNIQUE KEY uk_payroll_employee_company_tenant_period
        (employee_id, company_id, tenant_id, pay_period),
    UNIQUE KEY uk_payroll_payslip_number (payslip_number),
    CONSTRAINT fk_payrolls_employee_scope
        FOREIGN KEY (employee_id, company_id, tenant_id)
        REFERENCES employees (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
