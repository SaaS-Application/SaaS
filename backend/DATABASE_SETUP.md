# Database setup

Follow [`DATABASE_INSTALLATION.md`](./DATABASE_INSTALLATION.md) for the clean-install schema, installation prerequisites, verification queries, Hibernate guidance, and migration-management notes.

The clean-install schema now enforces the Super Admin tenant rule, attendance-to-leave-request employee/company/tenant ownership, attendance FK-supporting indexes, and one payroll row per employee and pay period. The existing payroll model has no payroll-run identifier, so multiple payroll runs for one employee in the same period are intentionally disallowed.

Do not use the historical `phase4-*`, `phase5-*`, or `phase6-*` manual scripts for a new empty database. They are upgrade scripts and are not part of the clean-install procedure.

The corrected clean-install schema remains statically reviewed only. It has not been executed against MySQL 8.0.39; live MySQL validation is pending on a separate disposable database. Do not treat H2 tests as proof of MySQL compatibility.

Phase 7 adds nullable payroll day-count and component-breakdown columns to the clean-install schema. Existing installations require review of `src/main/resources/db/manual/phase7-payroll-processing-mysql.sql`; do not execute it while database installation is paused. See [`PAYROLL_PROCESSING.md`](./PAYROLL_PROCESSING.md) for the calculation and API contract.
