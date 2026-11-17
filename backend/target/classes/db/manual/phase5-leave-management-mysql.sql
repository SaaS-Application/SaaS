-- Phase 5 additive migration for existing MySQL installations.
-- Back up and verify a restorable copy first. Stop the application and inspect
-- information_schema before running. This is a one-time migration: do not rerun
-- it against a partially migrated schema. No existing business rows are deleted.

-- Resolve existing orphaned or cross-company leave requests before proceeding.
SELECT r.id, r.employee_id, r.company_id, r.tenant_id
FROM leave_requests r
LEFT JOIN employees e
  ON e.id = r.employee_id
 AND e.company_id = r.company_id
 AND e.tenant_id = r.tenant_id
WHERE e.id IS NULL;

-- Review existing leave names for later manual classification; do not auto-map them.
SELECT leave_type, COUNT(*) AS application_count
FROM leave_requests
GROUP BY leave_type
ORDER BY leave_type;

-- Confirm all existing referenced tables are InnoDB and identifiers are compatible BIGINTs.
SELECT TABLE_NAME, ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('companies', 'employees', 'attendance', 'leave_requests', 'users');

-- Verify the composite keys required by the tenant-scoped foreign keys below.
-- Do not continue unless each required index exists in the listed order.
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS indexed_columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND (
       (TABLE_NAME = 'companies' AND INDEX_NAME = 'uk_company_id_tenant')
    OR (TABLE_NAME = 'employees' AND INDEX_NAME = 'uk_employee_id_company_tenant')
    OR (TABLE_NAME = 'attendance' AND INDEX_NAME = 'uk_attendance_employee_date')
  )
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;

-- Check for duplicate daily attendance before relying on its unique key.
SELECT employee_id, `date`, COUNT(*) AS duplicate_count
FROM attendance
GROUP BY employee_id, `date`
HAVING COUNT(*) > 1;

-- Check the status column width before using the Phase 5 status values.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'leave_requests'
  AND COLUMN_NAME IN ('days', 'status', 'created_at', 'company_id', 'tenant_id', 'employee_id');

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
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_type_company_code (company_id, code),
    UNIQUE KEY uk_leave_type_id_company_tenant (id, company_id, tenant_id),
    KEY idx_leave_type_company (company_id, tenant_id, active),
    CONSTRAINT fk_leave_type_company_tenant
        FOREIGN KEY (company_id, tenant_id) REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

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
        FOREIGN KEY (company_id, tenant_id) REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

ALTER TABLE leave_requests
    ADD COLUMN leave_type_id BIGINT NULL,
    MODIFY COLUMN days DECIMAL(7,2) NOT NULL,
    ADD COLUMN working_dates TEXT NULL,
    ADD COLUMN leave_paid BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN half_day BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN half_day_period VARCHAR(16) NULL,
    ADD COLUMN review_comment VARCHAR(1000) NULL,
    ADD COLUMN cancellation_comment VARCHAR(1000) NULL,
    ADD COLUMN supporting_document_key VARCHAR(36) NULL,
    ADD COLUMN reviewed_by BIGINT NULL,
    ADD COLUMN reviewed_at DATETIME NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD KEY idx_leave_request_company_status (company_id, tenant_id, status, start_date),
    ADD KEY idx_leave_request_employee_dates (employee_id, start_date, end_date),
    ADD CONSTRAINT fk_leave_request_employee_tenant
        FOREIGN KEY (employee_id, company_id, tenant_id)
        REFERENCES employees (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT fk_leave_request_type_scope
        FOREIGN KEY (leave_type_id, company_id, tenant_id)
        REFERENCES leave_types (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

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
    KEY idx_leave_balance_company (company_id, tenant_id, leave_year),
    CONSTRAINT fk_leave_balance_employee_tenant
        FOREIGN KEY (employee_id, company_id, tenant_id)
        REFERENCES employees (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_leave_balance_type_scope
        FOREIGN KEY (leave_type_id, company_id, tenant_id)
        REFERENCES leave_types (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE leave_balance_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    leave_balance_id BIGINT NOT NULL,
    leave_request_id BIGINT NULL,
    action VARCHAR(32) NOT NULL,
    paid_delta DECIMAL(7,2) NOT NULL DEFAULT 0,
    lop_delta DECIMAL(7,2) NOT NULL DEFAULT 0,
    actor_user_id BIGINT NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_balance_request_action
        (leave_request_id, action, leave_balance_id),
    KEY idx_leave_ledger_balance (leave_balance_id, created_at),
    CONSTRAINT fk_leave_ledger_balance
        FOREIGN KEY (leave_balance_id) REFERENCES leave_balances (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_leave_ledger_request
        FOREIGN KEY (leave_request_id) REFERENCES leave_requests (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

ALTER TABLE attendance
    ADD COLUMN leave_request_id BIGINT NULL,
    ADD COLUMN leave_day_fraction DECIMAL(3,2) NULL,
    ADD COLUMN leave_paid BOOLEAN NULL,
    ADD KEY idx_attendance_leave_request (leave_request_id),
    ADD CONSTRAINT fk_attendance_leave_request
        FOREIGN KEY (leave_request_id) REFERENCES leave_requests (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

-- Rollback: stop the application and restore the verified backup if rollback is
-- required. Leave the new tables and additive columns in place for an app-only
-- rollback; dropping them would discard leave data. No DROP statements are supplied.
