# Leave management behavior

## Policy and working-day calculation

Leave types are configured per company and snapshot their paid/unpaid state on each application. An application counts company working days only: configured weekly holidays and active company holidays are excluded. Without an attendance rule, Saturday and Sunday are weekly holidays. A half-day is one working date and counts as `0.50`; it requires a morning or afternoon period and a leave type that permits half-days. Applications are limited to 366 days and respect the leave type's past-date, consecutive-day, and supporting-document rules.

Balances are allocated per employee, leave type, and calendar year. Each application snapshots its working dates so later holiday or weekly-off changes cannot silently change the request's day count or yearly balance split. Each allocation, approval deduction, and cancellation reversal is recorded in the leave ledger. Approval requires an initialized balance for every affected calendar year for paid leave; unpaid leave is recorded as loss-of-pay (LOP). Pending requests are displayed separately and are not deducted. If a balance is insufficient, approval fails and does not overwrite existing attendance.

## Request lifecycle and attendance

Applications move through `PENDING`, `APPROVED`, `REJECTED`, or `WITHDRAWN`. An employee can withdraw a pending application. Future approved leave can be cancelled by request; a Company Admin or HR Admin approves or rejects the cancellation. Approved cancellation reverses the original ledger entries exactly once.

Approval creates linked attendance records with `ON_LEAVE` for full days and `HALF_DAY` for half-days. Configured holidays and weekly offs are not overwritten. If a normal attendance record already exists on a leave date, approval is rejected. Attendance corrections cannot change leave-generated records. Cancellation removes only leave-generated records that remain unchanged; changed records must be resolved before cancellation.

All employee operations resolve the employee profile through the authenticated account's explicit employee-user link. Company Admin and HR Admin operations are scoped to their active company and tenant. The frontend never supplies a trusted company or tenant ID. Super Admin access is read-only and requires an explicit company route.

## Supporting documents

Optional/required documents are accepted only as PDF, PNG, or JPEG, with MIME type and file signature checked and a 5 MB cap. Files are stored under `LEAVE_DOCUMENT_DIR` (default `./private/leave-documents`), outside static frontend resources, with random opaque keys. Back up this directory with the database and restrict filesystem access to the application service account. Downloads go through an authenticated, tenant-scoped endpoint.

## Existing MySQL installations

There is no migration history and the current MySQL schema has not been validated because MySQL authentication previously failed with `ERROR 1045`. Flyway is deferred until a DBA-reviewed baseline and upgrade/restore rehearsal are available. Hibernate `ddl-auto: update` remains the current schema mechanism, but is not a substitute for validating a production migration.

For an existing database, take and verify a restorable backup, stop the application, inspect the schema and all preflight queries in [`phase5-leave-management-mysql.sql`](./src/main/resources/db/manual/phase5-leave-management-mysql.sql), and resolve any orphaned or cross-company references before proceeding. Confirm the company/employee composite keys and attendance employee/date unique key exist, validate that existing `leave_requests` rows reference the correct employee/company/tenant, and verify all tables use InnoDB with compatible identifier types. The script is additive and does not delete legacy requests; it is a one-time migration and must not be rerun against a partially migrated schema. Classify legacy leave types and historical requests manually; the migration deliberately does not auto-map them. Do not execute it until the inspected schema is compatible.
