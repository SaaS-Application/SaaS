-- One-time schema migration for explicit employee/login identity.
-- Back up the database, stop the application, and inspect information_schema first.
-- This adds an optional link only; it deliberately does not update/link existing rows.
-- Do not run if employees.user_id or either named constraint already exists.

SELECT COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'employees'
  AND COLUMN_NAME = 'user_id';

SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND TABLE_NAME = 'employees'
  AND CONSTRAINT_NAME IN ('uk_employee_user', 'fk_employee_user');

ALTER TABLE employees
    ADD COLUMN user_id BIGINT NULL,
    ADD CONSTRAINT uk_employee_user UNIQUE (user_id),
    ADD CONSTRAINT fk_employee_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

-- Review-only candidate list. A row is eligible only when the corresponding user
-- has EMPLOYEE role, is enabled, shares the exact company and tenant, and both
-- sides have exactly one unlinked email match. Confirm each row with the business
-- owner before linking it through the authorized API.
SELECT e.id AS employee_id, e.employee_number, e.email,
       u.id AS user_id, u.username, e.company_id, e.tenant_id
FROM employees e
JOIN users u
  ON LOWER(TRIM(u.email)) = LOWER(TRIM(e.email))
 AND u.company_id = e.company_id
 AND u.tenant_id = e.tenant_id
 AND u.`role` = 'EMPLOYEE'
 AND u.enabled = TRUE
WHERE e.user_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM employees e2 WHERE e2.user_id = u.id)
  AND (SELECT COUNT(*) FROM employees e2
       WHERE e2.company_id = e.company_id
         AND e2.tenant_id = e.tenant_id
         AND LOWER(TRIM(e2.email)) = LOWER(TRIM(e.email))
         AND e2.user_id IS NULL) = 1
  AND (SELECT COUNT(*) FROM users u2
       WHERE u2.company_id = e.company_id
         AND u2.tenant_id = e.tenant_id
         AND u2.`role` = 'EMPLOYEE'
         AND u2.enabled = TRUE
         AND LOWER(TRIM(u2.email)) = LOWER(TRIM(e.email))) = 1;
