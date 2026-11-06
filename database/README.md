# Local MySQL database

The application uses the persistent local MySQL 8.4 database `company_management` by default.

- JDBC user: `company_app`
- JDBC password: `CompanyHub_Local_2026!`
- Server: `localhost:3306`
- MySQL data directory: `database/mysql-data` (ignored by Git)
- Server configuration: `database/mysql/my.ini`
- Startup script: `scripts/start-local-mysql.ps1`

Hibernate runs with `ddl-auto: validate` and `show-sql: false`. The running MySQL database is the schema and data source of truth. Application startup never creates tables or inserts demo rows. The `migrations` directory contains manual changes for existing databases.

`DataInitializer` requires the test-only `app.test-data.enabled=true` property from `src/test/resources`. It prepares isolated H2 data for automated tests and is never active when the packaged application connects to MySQL.

Automated tests continue to use isolated in-memory H2 databases and do not change this MySQL database.

## Demo data for September-November 2026

Run `scripts/seed-demo-sep-nov-2026.sql` against `company_management` to populate every calendar day for active employees. The script also adds representative leave, work-location, and attendance-adjustment requests. It can be rerun without duplicating rows. November dates after the import day are simulated records for demonstration.

## Company-wide project manager role

Apply `database/migrations/2026-11-06-portfolio-manager.sql` once to an existing MySQL database before creating a `PORTFOLIO_MANAGER` account. This role can manage projects, work requests, and tasks across departments, but cannot approve leave or manage users. The existing `PROJECT_MANAGER` role remains limited to project management within its department. HR can give final approval to leave requests after department-manager review.
