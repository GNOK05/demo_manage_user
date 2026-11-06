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

## Recent demo attendance

Use `scripts/seed-attendance-through-today.sql` as the single script for recent attendance data. It fills missing weekdays from 2026-11-03 and adds a small Saturday shift on 2026-11-07. Rerunning it leaves existing attendance and sessions unchanged. The SQL file is an import script; the running application's data lives in the MySQL `company_management` database.

## Work request workflow upgrade

For an existing MySQL database, back it up before upgrading. Run `scripts/migrate-workflow-tasks.sql` once to add task types, bug links, and request history. Then run `scripts/migrate-workflow-roles.sql` to migrate `MEMBER` to `DEV`/`BA` and `QA` to `TEST`. The latter keeps usernames and foreign keys intact and also permits `PORTFOLIO_MANAGER` in the database role constraint. Run these scripts before starting the updated backend because Hibernate uses `ddl-auto: validate`.

The role migration assigns one existing `staff_*` account per department to `BA` and the remaining specialists to `DEV`. It renames sample logins to `ba_*`, `dev_*`, and `test_*` without changing user IDs or password hashes. An admin can adjust individual assignments in the People screen. A local `portfolio_manager` account was created separately through the user API; the migration does not create it on other databases.

## Demo data for September-November 2026

Run `scripts/seed-demo-sep-nov-2026.sql` against `company_management` to populate every calendar day for active employees. The script also adds representative leave, work-location, and attendance-adjustment requests. It can be rerun without duplicating rows. November dates after the import day are simulated records for demonstration.

## Company-wide project manager role

Apply `database/migrations/2026-11-06-portfolio-manager.sql` once to an existing MySQL database before creating a `PORTFOLIO_MANAGER` account. This role can manage projects, work requests, and tasks across departments, but cannot approve leave or manage users. The existing `PROJECT_MANAGER` role remains limited to project management within its department. HR can give final approval to leave requests after department-manager review.
