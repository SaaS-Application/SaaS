-- Add payroll day counts and immutable salary-component snapshots.
-- Run once on an existing installation after database change approval.
-- Historical payroll rows remain NULL for the newly added fields because their
-- original attendance and component snapshots cannot be reconstructed safely.
ALTER TABLE payrolls
    ADD COLUMN total_working_days DECIMAL(7,2) NULL,
    ADD COLUMN paid_days DECIMAL(7,2) NULL,
    ADD COLUMN unpaid_days DECIMAL(7,2) NULL,
    ADD COLUMN component_breakdown_json TEXT NULL;
