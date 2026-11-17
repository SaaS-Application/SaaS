-- Phase 6 additive migration. Back up and inspect the current database first.
-- Do not apply until the preflight queries show the required composite keys.
-- This creates new salary-management tables and intentionally leaves the
-- legacy salary_components and payrolls tables unchanged.

SELECT TABLE_NAME, ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('companies', 'employees', 'salary_components');

SELECT TABLE_NAME, INDEX_NAME,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS indexed_columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND (
       (TABLE_NAME = 'companies' AND INDEX_NAME = 'uk_company_id_tenant')
    OR (TABLE_NAME = 'employees' AND INDEX_NAME = 'uk_employee_id_company_tenant')
  )
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;

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
    CONSTRAINT fk_salary_component_company_tenant
        FOREIGN KEY (company_id, tenant_id) REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_salary_component_type CHECK (type IN ('EARNING', 'DEDUCTION')),
    CONSTRAINT chk_salary_component_method CHECK (calculation_method IN ('FIXED_AMOUNT', 'PERCENTAGE')),
    CONSTRAINT chk_salary_component_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB;

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
    CONSTRAINT fk_salary_structure_company_tenant
        FOREIGN KEY (company_id, tenant_id) REFERENCES companies (id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_salary_structure_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB;

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
) ENGINE=InnoDB;

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
    KEY idx_salary_assignment_company_period (company_id, tenant_id, active, effective_from),
    CONSTRAINT fk_salary_assignment_employee_scope
        FOREIGN KEY (employee_id, company_id, tenant_id)
        REFERENCES employees (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_salary_assignment_structure_scope
        FOREIGN KEY (salary_structure_id, company_id, tenant_id)
        REFERENCES salary_structures (id, company_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_salary_assignment_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_salary_assignment_amounts
        CHECK (gross_earnings >= 0 AND total_deductions >= 0)
) ENGINE=InnoDB;

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
    CONSTRAINT chk_salary_assignment_component_amount CHECK (calculated_amount >= 0)
) ENGINE=InnoDB;

-- MySQL has no exclusion constraint for date-range overlaps. The service locks
-- the employee row and rejects overlapping active assignment periods.
-- Roll back the application without dropping these tables to preserve salary
-- history. Do not execute DROP statements as an application rollback.
