# Payroll processing

## Workflow and access

Company Admins and HR Admins can preview, process, and read payroll for their authenticated company. Company and tenant identity are derived from the authenticated user. The optional legacy `companyId` on the company payroll list is accepted only as a consistency check and cannot set or expand the data scope. Suspended companies are rejected by the existing tenant security check.

Employees can read only payroll records linked to their own employee profile. Super Admins can read platform-wide payroll records, optionally filtered by company and pay period, but have no payroll write endpoint.

| Method | Endpoint | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/api/company-admin/payroll-periods/{YYYY-MM}/preview` | COMPANY_ADMIN, HR_ADMIN | Preview employee calculations and validation warnings |
| POST | `/api/company-admin/payroll-periods/{YYYY-MM}/process` | COMPANY_ADMIN, HR_ADMIN | Atomically process eligible employees in a completed period |
| GET | `/api/company-admin/payrolls?payPeriod=YYYY-MM` | COMPANY_ADMIN, HR_ADMIN | List company payroll; period filter is optional. A legacy optional `companyId` query parameter is accepted only when it matches the authenticated company; it never sets the data scope. |
| GET | `/api/company-admin/payrolls/{payrollId}` | COMPANY_ADMIN, HR_ADMIN | Read a company payroll and saved component breakdown |
| GET | `/api/employee/payrolls` | EMPLOYEE | List the authenticated employee's payroll history |
| GET | `/api/employee/payrolls/{payrollId}` | EMPLOYEE | Read only the authenticated employee's payroll |
| GET | `/api/super-admin/payrolls?companyId={id}&payPeriod=YYYY-MM` | SUPER_ADMIN | Read-only global list; both filters are optional |
| GET | `/api/super-admin/payrolls/{payrollId}` | SUPER_ADMIN | Read-only individual payroll oversight |

Responses use payroll DTOs and do not expose JPA entities. Invalid periods or incomplete payroll data return a validation error, duplicate employee-period records return a conflict, and out-of-scope detail requests return not found.

## Calculation rules

1. A pay period uses the strict `YYYY-MM` format and must be complete before processing.
2. Expected working dates are the calendar dates in the month excluding the company's configured weekly holidays and active company holidays. For an employee hired during the period, dates before `dateOfJoining` are excluded.
3. Attendance must exist for every expected working date. Manual attendance must be approved; only `NOT_REQUIRED` and `APPROVED` records are accepted. Missing, pending, rejected, or inconsistent leave-attendance records block processing for the batch.
4. `PRESENT` and `LATE` count as one paid day; `ABSENT` counts as zero. A manual `HALF_DAY` counts as 0.5. Approved half-day leave counts the worked half plus the leave half only when that leave is paid. Approved full-day leave counts its recorded day fraction when paid and zero when unpaid. A leave attendance must match an approved (or cancellation-pending) leave request for the same employee, company, and tenant.
5. Exactly one effective salary assignment must cover the employee's configured working dates. Missing assignments make that employee ineligible; a mid-period gap or assignment change blocks processing rather than guessing how to split monthly salary snapshots.
6. Each earning component is prorated as `monthly component amount × paid days ÷ configured working days`, rounded to two decimal places using `HALF_UP`.
7. Assigned deduction components remain at their full monthly snapshot amount; they are not prorated and no additional LOP or statutory deduction is invented.
8. Gross earnings are the sum of prorated earnings; total deductions are the sum of assigned deductions; net salary is gross earnings minus total deductions. Each component and total is checked against the existing signed `DECIMAL(14,2)` range before persistence.

No income-tax, PF, ESI, professional-tax, or other statutory rates are applied. The model has no company-currency setting; values are stored and displayed as currency amounts without conversion or an assumed currency symbol.

Employees with missing salary assignments or already-processed records are skipped and reported. Other eligible employees may be processed when there are no batch-blocking attendance or configuration errors. Records created by one processing request and its audit entry share a transaction; a persistence or audit failure rolls back the batch. The existing unique key remains one payroll per employee, company, tenant, and period; reruns are not supported.

## Database changes

The payroll table gains nullable `total_working_days`, `paid_days`, `unpaid_days` (`DECIMAL(7,2)`) and `component_breakdown_json` (`TEXT`) columns. New payroll rows populate them; nullable mappings preserve existing installations and historical records whose day counts or component snapshots cannot be reconstructed.

- New empty schemas: the columns are included in `src/main/resources/db/manual/clean-install-mysql.sql`.
- Existing schemas: review and later execute the one-time additive script `src/main/resources/db/manual/phase7-payroll-processing-mysql.sql` only after database installation is approved. It has not been executed. Do not use it as a clean-install script or rerun it after application.
- H2-backed tests do not establish MySQL compatibility. No live MySQL schema validation is claimed for this phase.
