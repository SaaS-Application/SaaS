# Salary structures

Phase 6 provides salary component definitions, salary structures, effective-dated employee assignments, and read-only salary views. It does not process payroll, create payslips, calculate tax, or make statutory deductions.

## Calculation rules

- All server-side monetary arithmetic uses `BigDecimal`.
- Fixed amounts accept at most two decimal places. Percentage rates accept at most four.
- Percentage results and totals are rounded to two decimal places using `HALF_UP`.
- Percentage bases must be earning components included in the same structure. Cyclic bases are rejected.
- A percentage is applied to the calculated amount of its selected earning base; there are no implicit statutory rates.
- `illustrativeNet` is gross earnings minus configured deductions. It is an illustration, not payroll output or a tax result.
- Components and structures have inclusive effective dates. An assignment must fit within its structure and each component's effective period.

## Tenancy and history

Company and tenant identifiers are derived from the authenticated account. Company Admin and HR Admin operations are scoped to their active company. Employees can only read salary history for their explicitly linked employee profile. Super Admin can read aggregate company counts only and cannot create or change salary data.

Assignments are locked per employee while checking date overlap. Reassigning an employee can close a preceding open-ended assignment on the day before the new effective date; overlapping/future assignment conflicts are rejected. Assignment component values, names, codes, and calculated illustrative amounts are snapshotted so subsequent structure changes do not rewrite salary history.

Administrative creation, update, activation/deactivation, and assignment actions are recorded in the existing audit log.

## API

All routes are under `/api` and use the application's JWT authentication.

| Method | Path | Access |
| --- | --- | --- |
| GET, POST | `/company-admin/salary/components` | COMPANY_ADMIN, HR_ADMIN |
| PUT, PATCH | `/company-admin/salary/components/{componentId}`, `/company-admin/salary/components/{componentId}/status` | COMPANY_ADMIN, HR_ADMIN |
| GET, POST | `/company-admin/salary/structures` | COMPANY_ADMIN, HR_ADMIN |
| GET, PUT, PATCH | `/company-admin/salary/structures/{structureId}`, `/company-admin/salary/structures/{structureId}/status` | COMPANY_ADMIN, HR_ADMIN |
| POST | `/company-admin/salary/assignments` | COMPANY_ADMIN, HR_ADMIN |
| GET | `/company-admin/salary/employees/{employeeId}/assignments` | COMPANY_ADMIN, HR_ADMIN; own company |
| GET | `/employee/salary/current`, `/employee/salary/history` | EMPLOYEE; linked profile only |
| GET | `/super-admin/salary/summary` | SUPER_ADMIN; aggregate read-only |

OpenAPI documentation is available through the existing Springdoc endpoints (`/v3/api-docs` and `/swagger-ui.html`).

## MySQL schema rollout

The project has no migration runner; it uses Hibernate `ddl-auto: update` and reviewed manual SQL. The Phase 6 migration is additive and leaves the legacy `salary_components` and payroll tables untouched:

`src/main/resources/db/manual/phase6-salary-structure-mysql.sql`

Before applying it to an existing MySQL 8 database:

1. Back up the database and confirm the selected database/schema.
2. Run the migration's read-only preflight queries. Verify that `companies`, `employees`, and (if present) the legacy `salary_components` table have their expected engines and that the required company/tenant and employee/company/tenant unique keys are present.
3. Apply the SQL before starting the upgraded application for the first time: the current Hibernate `ddl-auto: update` setting may create the tables without the migration's composite foreign keys.
4. If tables already exist from a prior application start or migration attempt, inspect their columns, indexes, and foreign keys and reconcile them deliberately; do not rerun the `CREATE TABLE` statements blindly.
5. Confirm MySQL 8.0.16+ for enforced `CHECK` constraints.
6. Apply the script during a controlled maintenance window, then verify every created foreign key, unique key, and index in `information_schema`.

The application also serializes assignment changes by locking the employee row and rejects overlapping effective periods; MySQL does not provide a date-range exclusion constraint. The migration has not been applied or live-verified against MySQL. The existing environment previously returned MySQL authentication error 1045.
