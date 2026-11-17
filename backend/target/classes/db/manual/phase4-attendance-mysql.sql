-- One-time migration for an existing pre-Phase-4 attendance table.
-- Back up the database and stop the application. Inspect information_schema first;
-- do not run this file against a new schema or a partially migrated schema.
-- Anomaly queries must return zero rows. Inventory queries must show the expected
-- tables/keys. Reconcile existing named constraints before running any ALTER.

-- Employee numbers are unique within a company, including case/space variants.
SELECT company_id, UPPER(TRIM(employee_number)) AS normalized_employee_number,
       COUNT(*) AS row_count
FROM employees
GROUP BY company_id, UPPER(TRIM(employee_number))
HAVING COUNT(*) > 1;

-- Duplicate daily records cannot be resolved by this migration without data loss.
SELECT employee_id, `date`, COUNT(*) AS row_count
FROM attendance
GROUP BY employee_id, `date`
HAVING COUNT(*) > 1;

-- Existing employee/company/tenant values must agree before the composite FK is added.
SELECT a.id, a.employee_id, a.company_id, a.tenant_id
FROM attendance a
LEFT JOIN employees e
  ON e.id = a.employee_id
 AND e.company_id = a.company_id
 AND e.tenant_id = a.tenant_id
WHERE e.id IS NULL;

-- Verify all involved tables use InnoDB and that these columns have compatible BIGINT types.
SELECT TABLE_NAME, ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('attendance', 'employees', 'companies');

-- Check for keys that this script adds, and do not add a key that already exists.
SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND (
       (TABLE_NAME = 'attendance' AND CONSTRAINT_NAME IN
            ('uk_attendance_employee_date', 'fk_attendance_employee_tenant'))
    OR (TABLE_NAME = 'employees' AND CONSTRAINT_NAME = 'uk_employee_id_company_tenant')
    OR (TABLE_NAME = 'companies' AND CONSTRAINT_NAME = 'uk_company_id_tenant')
    OR (TABLE_NAME = 'attendance_rules' AND CONSTRAINT_NAME IN
            ('uk_attendance_rules_company', 'fk_attendance_rules_company_tenant'))
  )
ORDER BY TABLE_NAME, CONSTRAINT_NAME;

-- Do not apply a UTC default to historical local wall-clock times unless UTC is confirmed.
-- Choose the correct default for this data before applying the ALTER.

ALTER TABLE attendance
    ADD COLUMN total_working_minutes INT NOT NULL DEFAULT 0,
    ADD COLUMN check_in_instant TIMESTAMP(6) NULL,
    ADD COLUMN check_out_instant TIMESTAMP(6) NULL,
    ADD COLUMN time_zone_id VARCHAR(64) NOT NULL DEFAULT 'UTC',
    ADD COLUMN late_arrival BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN early_departure BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN overtime_minutes INT NOT NULL DEFAULT 0,
    ADD COLUMN notes VARCHAR(1000) NULL,
    ADD COLUMN approval_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL,
    ADD COLUMN approved_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT uk_attendance_employee_date UNIQUE (employee_id, `date`),
    ADD INDEX idx_attendance_company_date (company_id, tenant_id, `date`);

-- The referenced composite keys are redundant with the primary IDs, but are required
-- to make tenant/company consistency enforceable at the database boundary.
ALTER TABLE employees
    ADD CONSTRAINT uk_employee_id_company_tenant
        UNIQUE (id, company_id, tenant_id);

ALTER TABLE attendance
    ADD CONSTRAINT fk_attendance_employee_tenant
        FOREIGN KEY (employee_id, company_id, tenant_id)
        REFERENCES employees (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE companies
    ADD CONSTRAINT uk_company_id_tenant
        UNIQUE (id, tenant_id);

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
    CONSTRAINT uk_attendance_rules_company UNIQUE (company_id),
    CONSTRAINT fk_attendance_rules_company_tenant
        FOREIGN KEY (company_id, tenant_id)
        REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;
