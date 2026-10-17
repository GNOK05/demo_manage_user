-- Apply after the 20260927 task migration.
-- DataInitializer will create/update the demo usernames and BCrypt passwords at application startup.

ALTER TABLE users DROP CHECK chk_user_role;

UPDATE users SET role = 'TEAM_LEAD' WHERE role = 'MANAGER';
UPDATE users SET role = 'MEMBER' WHERE role = 'EMPLOYEE';

ALTER TABLE users
  ADD CONSTRAINT chk_user_role
  CHECK (role IN ('ADMIN', 'HR', 'PROJECT_MANAGER', 'TEAM_LEAD', 'MEMBER', 'QA', 'VIEWER'));

ALTER TABLE tasks DROP CHECK chk_task_status;

ALTER TABLE tasks
  MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'BACKLOG',
  ADD CONSTRAINT chk_task_status
  CHECK (status IN ('BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'TESTING', 'BLOCKED', 'DONE', 'CANCELLED'));
