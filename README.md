# Payroll SaaS Platform

This project contains a Spring Boot 3 backend and React frontend for a multi-tenant payroll SaaS application.

## Architecture

- Backend: Java 17, Spring Boot 3, Spring Security, JWT, Spring Data JPA
- Database: MySQL 8.x with tenant-aware record boundaries
- Frontend: React + Vite
- Security: BCrypt password hashing, JWT bearer tokens, tenant validation, audited admin actions

## Backend

```bash
cd backend
mvn test
mvn spring-boot:run
```

Configure MySQL before running the app. Set database and bootstrap secrets through the runtime environment or an approved secret manager; do not commit them:

`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `SUPER_ADMIN_USERNAME`, `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD`.

See [backend/DATABASE_SETUP.md](./backend/DATABASE_SETUP.md) for clean-install and schema-verification guidance. The corrected clean-install SQL has not been executed against MySQL; live validation remains pending on a separate disposable MySQL 8.0.39 database.

## Payroll processing

Monthly payroll preview and processing reuse the existing effective-dated salary assignments, attendance rules, holidays, and approved leave attendance. Earning components are prorated by paid working days; assigned deductions remain at their full monthly amounts. Company Admins and HR Admins process their own company's payroll, employees can view their own payroll history, and Super Admin access is read-only. See [backend/PAYROLL_PROCESSING.md](./backend/PAYROLL_PROCESSING.md) for calculation rules, endpoints, validation behavior, and the additive schema script. The schema changes have not been applied to MySQL.

## Frontend

```bash
cd frontend
npm install
npm run build
npm run dev
```

## Phase 2 features implemented

- Secure email-based login with JWT issuance and validation
- BCrypt-backed authentication and password hashing
- Forgot password and reset password flows
- Change password support and logout token invalidation
- Bootstrap mechanism for the initial super admin account
- Global super admin dashboard and company registration APIs
- Multi-tenant company and admin onboarding with tenant id enforcement
- Basic React login and dashboard UI with protected navigation

## Important notes

- Tenant boundaries are enforced from the authenticated principal rather than incoming request values.
- Frontend views are modular, with role-specific navigation for the implemented payroll processing and self-service views.
