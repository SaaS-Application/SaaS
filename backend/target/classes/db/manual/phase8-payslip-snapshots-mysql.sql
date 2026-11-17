-- Add immutable payslip identity/profile snapshots for newly processed payrolls.
-- Existing rows remain NULL; do not infer historical details from current profiles.
ALTER TABLE payrolls
    ADD COLUMN payslip_number VARCHAR(42) NULL,
    ADD COLUMN company_name_snapshot VARCHAR(255) NULL,
    ADD COLUMN company_address_snapshot VARCHAR(255) NULL,
    ADD COLUMN employee_name_snapshot VARCHAR(511) NULL,
    ADD COLUMN employee_number_snapshot VARCHAR(255) NULL,
    ADD COLUMN department_name_snapshot VARCHAR(255) NULL,
    ADD COLUMN designation_name_snapshot VARCHAR(255) NULL,
    ADD UNIQUE KEY uk_payroll_payslip_number (payslip_number);
