# Local MySQL database

The application uses the persistent local MySQL 8.4 database `company_management` by default.

- JDBC user: `company_app`
- JDBC password: `CompanyHub_Local_2026!`
- Server: `localhost:3306`
- MySQL data directory: `database/mysql-data` (ignored by Git)
- Server configuration: `database/mysql/my.ini`
- Startup script: `scripts/start-local-mysql.ps1`

Hibernate runs with `ddl-auto: validate` and `show-sql: false`. The running MySQL database is the schema and data source of truth. The repository contains no schema-generation or migration SQL, and application startup never creates tables or inserts demo rows.

`DataInitializer` requires the test-only `app.test-data.enabled=true` property from `src/test/resources`. It prepares isolated H2 data for automated tests and is never active when the packaged application connects to MySQL.

Automated tests continue to use isolated in-memory H2 databases and do not change this MySQL database.
