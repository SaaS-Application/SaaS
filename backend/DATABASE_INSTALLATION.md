# Payroll SaaS clean-install database guide

## Status and scope

This guide covers a **new, empty** MySQL 8.0.39 `payroll_saas` database. The consolidated DDL is:

- [`src/main/resources/db/manual/clean-install-mysql.sql`](./src/main/resources/db/manual/clean-install-mysql.sql)

It represents the current entity/table mappings and completed attendance, leave, and salary-structure modules. It is not a data migration and must not be used on a database with existing tables.

The schema has been reviewed statically against the JPA entities, repositories, and four historical SQL scripts. It has **not** been executed or tested against MySQL. Do not treat this review as proof that the script installs successfully. The documented limitations must be considered before production use, and the script must first be tested on a disposable MySQL 8.0.39 database.

The four scripts in `src/main/resources/db/manual/` are **upgrade-only** and must not be run as part of this clean install:

- `phase4-attendance-mysql.sql`
- `phase4-1-employee-user-link-mysql.sql`
- `phase5-leave-management-mysql.sql`
- `phase6-salary-structure-mysql.sql`

The clean-install file creates tables in foreign-key dependency order and includes the completed-phase schema. It intentionally does not create the database itself.

## Entity relationships and precision

The ledger relationship gap is addressed in the current entities and schema: each transaction’s balance association supplies employee/company/tenant scope, its optional request association must match the same scope, and the ledger service validates that the actor belongs to the balance company and tenant. The clean-install DDL adds composite database FKs enforcing that the balance, optional request, and actor rows match the transaction’s employee/company/tenant scope. The JPA actor association itself joins by user ID; Hibernate `validate` does not verify the additional composite actor FK.

Monetary precision is now explicit:

| Meaning | JPA/SQL precision |
| --- | --- |
| Currency values in `SalaryComponent.amount`, payroll amounts, salary-assignment totals, and calculated component amounts | `DECIMAL(14,2)` |
| `component_value`, which carries either a fixed currency amount or a percentage rate | `DECIMAL(14,4)` |

`component_value` is intentionally polymorphic to preserve the existing DTO/entity model. `SalaryStructureService` requires fixed amounts to have at most 2 fractional digits and percentage rates at most 4; calculations round computed currency amounts to 2 places using `HALF_UP`. The current request validation permits up to 10 integer digits for component values, consistent with `DECIMAL(14,4)`.

The maximum `DECIMAL(14,2)` currency value is `999999999999.99` (minimum `-999999999999.99`; negative net salary is permitted only within that lower bound); the maximum nonnegative `DECIMAL(14,4)` component value is `9999999999.9999`. The service checks each rounded calculated component amount and the gross, deduction, and net totals against the currency range before persisting structure data or salary assignments. Out-of-range values are rejected; they are not clamped or truncated. Percentage rates retain their independent four-decimal precision, so a valid rate/base pair can still produce a rejected out-of-range currency amount.

Remaining design limitations:

1. **Assignment overlap is not a declarative MySQL constraint.** MySQL has no exclusion constraint for arbitrary date ranges. Continue to rely on the service’s employee-row lock and overlap validation, and cover it with concurrency tests.
2. **Application-level rules remain necessary.** A database cannot infer salary percentage-base type rules, employee account activation state, or role authorization from these FKs. Keep existing service validation and authorization in place.

Other tenant-bearing relations use composite FKs where the current columns permit: employee-to-company/user/department/designation/manager, attendance-to-employee/leave request and actor, leave requests/balances/ledger-to-employee/leave type/request/actor, company-scoped settings, and salary structures/components/assignments. The attendance-to-leave-request FK includes employee, company, and tenant identity so a record cannot point to another employee's request in the same company. All identifiers and FKs use signed `BIGINT`, matching Java `Long`; scoped parent indexes use the same column order as their FKs.

The payroll table and JPA mapping enforce one record per employee, company, tenant, and pay period. The model has no payroll-run identifier, so multiple records for separate runs in the same period are intentionally disallowed; if reruns or parallel runs become legitimate, add an explicit run/version model before relaxing the unique key.

Phase 7 adds nullable `total_working_days`, `paid_days`, and `unpaid_days` (`DECIMAL(7,2)`) plus `component_breakdown_json` (`TEXT`) to payroll records. The clean-install script contains these columns. Existing installations require the additive, one-time `src/main/resources/db/manual/phase7-payroll-processing-mysql.sql` after separate database approval. Historical rows remain NULL in these columns because their attendance and component snapshots cannot be safely reconstructed.

## Installation procedure

1. **Confirm this is a clean target.** The owner/DBA must verify that `payroll_saas` is the intended schema and has no tables or data. Do not infer this from an earlier inspection.
2. **Create the database separately** with the approved `utf8mb4` charset and `utf8mb4_0900_ai_ci` collation. The application runtime account should not be relied on to create databases.
3. **Test the DDL in a disposable MySQL 8.0.39 database first.** Review the limitations above and capture the resulting table, index, FK, and CHECK inventories. MySQL DDL is not a single rollbackable transaction; on partial failure, inspect and reconcile rather than rerunning blindly.
4. **Run only** `clean-install-mysql.sql`, once, against the confirmed empty `payroll_saas` schema. The file selects `payroll_saas` itself.
5. **Run the verification checklist below** and resolve any mismatch before starting application instances.
6. Configure `spring.jpa.hibernate.ddl-auto: validate`, then start the application only after validation on the installed schema succeeds. Do not use `update` as the clean-install mechanism.

Example from the repository root in Windows Command Prompt (the MySQL client prompts for the password; do not put it in the command):

```text
mysql --host=localhost --port=3306 --user=payroll_app --password --database=payroll_saas < backend\src\main\resources\db\manual\clean-install-mysql.sql
```

Use the approved secret mechanism and account; never place credentials in this document, shell history, or logs. For schema installation, the account needs the database-level privileges required to create tables, indexes, and foreign keys. Keep schema-changing privileges separate from routine application access when operationally possible.

## Hibernate schema management

After the reviewed DDL has been installed and verified, `spring.jpa.hibernate.ddl-auto: validate` is the recommended runtime setting. It makes startup fail on mapped table/column incompatibilities instead of silently attempting schema changes.

`validate` does not prove that every manual CHECK, composite FK, or secondary index exists. Keep the explicit database verification below. It also must not be enabled before the schema is installed, because the application will fail startup against an empty database.

The current `application.yml` still uses `ddl-auto: update`; it is intentionally unchanged in this preparation task. Change it in a separately approved deployment step after the schema has been installed and successfully validated. Also remove the production reliance on the `createDatabaseIfNotExist` JDBC option if it is enabled in the deployed configuration.

## Flyway assessment

Flyway is not currently configured or present in `backend/pom.xml`. It is appropriate as a future migration manager, but it is **not introduced here**: the existing scripts are manual, non-idempotent, upgrade-only deltas and do not provide a clean-install baseline. Automatically adding them as Flyway versions risks duplicate columns/tables and partial upgrades.

A safe future adoption would require:

1. Test and review this clean-install DDL on disposable MySQL 8.0.39.
2. Decide whether to use the consolidated script as `V1__clean_baseline.sql` or split it into ordered, separately testable versions.
3. Add the Flyway MySQL module compatible with the project's Spring Boot-managed version, configure migration locations, and ensure Flyway runs before Hibernate validation.
4. Set Hibernate to `validate` and keep runtime DB privileges limited.
5. For any already-provisioned database, inventory it first and baseline it deliberately. Do not enable automatic baseline-on-migrate or replay the historical scripts without a tested upgrade path.

## Verification checklist

Run these read-only checks after installation. Replace no names in these queries unless the approved schema name changes.

### Server, selected schema, table count, and engines

```sql
SELECT DATABASE() AS selected_database, VERSION() AS mysql_version,
       @@sql_mode AS sql_mode, @@time_zone AS session_time_zone,
       @@system_time_zone AS system_time_zone;

SELECT COUNT(*) AS base_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE';

SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;
```

The script is expected to create 22 InnoDB entity tables: `attendance`, `attendance_rules`, `audit_logs`, `companies`, `company_holidays`, `departments`, `designations`, `employees`, `leave_balance_transactions`, `leave_balances`, `leave_requests`, `leave_types`, `password_reset_tokens`, `payrolls`, `salary_assignment_components`, `salary_assignments`, `salary_component_definitions`, `salary_components`, `salary_structure_components`, `salary_structures`, `token_blacklist`, and `users`.

### Foreign keys, indexes, and CHECK constraints

```sql
SELECT k.TABLE_NAME, k.CONSTRAINT_NAME, k.COLUMN_NAME,
       k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME,
       k.ORDINAL_POSITION, r.UPDATE_RULE, r.DELETE_RULE
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS r
  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
 AND r.TABLE_NAME = k.TABLE_NAME
 AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
WHERE k.CONSTRAINT_SCHEMA = DATABASE()
  AND k.REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION;

SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS indexed_columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE
ORDER BY TABLE_NAME, INDEX_NAME;

SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE
FROM information_schema.TABLE_CONSTRAINTS tc
JOIN information_schema.CHECK_CONSTRAINTS cc
  ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
 AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
  AND tc.CONSTRAINT_TYPE = 'CHECK'
ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME;
```

Confirm every FK is present with the expected ordered columns, every table uses InnoDB, and MySQL reports the expected CHECK constraints. Inspect any InnoDB-created FK indexes as well as explicitly named indexes.

### Column type and numeric precision

```sql
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND (
       (TABLE_NAME IN ('companies', 'users', 'employees') AND
        COLUMN_NAME IN ('id', 'tenant_id', 'company_id', 'user_id', 'department_id', 'designation_id'))
    OR (TABLE_NAME IN ('leave_balances', 'leave_requests', 'leave_balance_transactions') AND
        COLUMN_NAME IN ('id', 'employee_id', 'company_id', 'tenant_id', 'leave_type_id',
                        'leave_balance_id', 'leave_request_id', 'actor_user_id',
                        'days', 'paid_delta', 'lop_delta'))
    OR (TABLE_NAME LIKE 'salary%' OR TABLE_NAME IN ('payrolls')) AND
        COLUMN_NAME IN ('id', 'company_id', 'tenant_id', 'employee_id', 'component_id',
                        'salary_structure_id', 'salary_assignment_id', 'component_value',
                        'amount', 'gross_salary', 'deductions', 'net_salary', 'calculated_amount')
  )
ORDER BY TABLE_NAME, ORDINAL_POSITION;
```

Check that all identifier/FK columns have compatible signedness and size. Confirm salary/leave decimal precision and scale against the agreed financial ranges and JPA annotations.

### Relationship integrity queries

These queries should return zero rows after any test data is created:

```sql
SELECT e.id
FROM employees e
LEFT JOIN companies c
  ON c.id = e.company_id AND c.tenant_id = e.tenant_id
WHERE c.id IS NULL;

SELECT e.id
FROM employees e
JOIN users u ON u.id = e.user_id
WHERE e.user_id IS NOT NULL
  AND (u.company_id <> e.company_id OR u.tenant_id <> e.tenant_id OR u.role <> 'EMPLOYEE');

SELECT a.id
FROM attendance a
LEFT JOIN employees e
  ON e.id = a.employee_id AND e.company_id = a.company_id AND e.tenant_id = a.tenant_id
WHERE e.id IS NULL;

SELECT r.id
FROM leave_requests r
LEFT JOIN employees e
  ON e.id = r.employee_id AND e.company_id = r.company_id AND e.tenant_id = r.tenant_id
WHERE e.id IS NULL;

SELECT b.id
FROM leave_balances b
LEFT JOIN employees e
  ON e.id = b.employee_id AND e.company_id = b.company_id AND e.tenant_id = b.tenant_id
LEFT JOIN leave_types t
  ON t.id = b.leave_type_id AND t.company_id = b.company_id AND t.tenant_id = b.tenant_id
WHERE e.id IS NULL OR t.id IS NULL;

SELECT a.id
FROM salary_assignments a
LEFT JOIN employees e
  ON e.id = a.employee_id AND e.company_id = a.company_id AND e.tenant_id = a.tenant_id
LEFT JOIN salary_structures s
  ON s.id = a.salary_structure_id AND s.company_id = a.company_id AND s.tenant_id = a.tenant_id
WHERE e.id IS NULL OR s.id IS NULL;

SELECT a.id
FROM attendance a
JOIN leave_requests r ON r.id = a.leave_request_id
WHERE a.leave_request_id IS NOT NULL
  AND (a.employee_id <> r.employee_id
       OR a.company_id <> r.company_id
       OR a.tenant_id <> r.tenant_id);

SELECT t.id
FROM leave_balance_transactions t
LEFT JOIN leave_balances b
  ON b.id = t.leave_balance_id AND b.employee_id = t.employee_id
 AND b.company_id = t.company_id AND b.tenant_id = t.tenant_id
LEFT JOIN users actor
  ON actor.id = t.actor_user_id AND actor.company_id = t.company_id
 AND actor.tenant_id = t.tenant_id
LEFT JOIN leave_requests r
  ON r.id = t.leave_request_id AND r.employee_id = t.employee_id
 AND r.company_id = t.company_id AND r.tenant_id = t.tenant_id
WHERE b.id IS NULL OR actor.id IS NULL
   OR (t.leave_request_id IS NOT NULL AND r.id IS NULL);
```

Also verify the attendance unique key with a duplicate-insert test on disposable data, and verify salary assignment overlap behavior through the application test suite. Do not use real employee/payroll data for these probes.
Also verify that duplicate payroll rows for the same employee and pay period are rejected, while the same period for different employees remains allowed.

## Installation result

- DDL prepared: yes, static review only.
- H2-backed backend tests: `mvn clean test` passed, 79 tests, 0 failures, 0 errors, 0 skipped.
- Frontend production build: `npm run build` passed.
- Test limitation: H2 test results do not establish MySQL compatibility or verify the manual DDL.
- MySQL execution: not performed; the corrected clean-install script remains pending verification on a separate disposable MySQL 8.0.39 database.
- Spring Boot startup: not performed.
- Legacy upgrade scripts: not executed or modified.
- Production readiness: pending successful installation and verification on disposable MySQL 8.0.39, followed by approved deployment against the target database.
