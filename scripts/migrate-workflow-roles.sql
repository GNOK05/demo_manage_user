-- Preserve usernames and foreign keys while assigning specialized roles.
ALTER TABLE users DROP CHECK chk_user_role;
ALTER TABLE users ADD CONSTRAINT chk_user_role CHECK
    (role IN ('ADMIN','HR','PORTFOLIO_MANAGER','PROJECT_MANAGER','TEAM_LEAD','BA','DEV','TEST','MEMBER','QA','VIEWER'));
START TRANSACTION;
UPDATE users SET role = 'DEV' WHERE role = 'MEMBER' AND job_title = 'Developer';
UPDATE users SET role = 'BA', job_title = 'Business Analyst'
WHERE role = 'MEMBER' AND username LIKE 'staff_%'
  AND id IN (SELECT id FROM (SELECT MIN(id) id FROM users WHERE role = 'MEMBER' AND username LIKE 'staff_%' GROUP BY department_id) selected);
UPDATE users SET role = 'DEV', job_title = 'Developer' WHERE role = 'MEMBER';
UPDATE users SET role = 'TEST', job_title = 'Tester' WHERE role = 'QA';
UPDATE users SET username = CONCAT('dev_', SUBSTRING(username, 8)) WHERE username LIKE 'member_%' AND role = 'DEV';
UPDATE users SET username = CONCAT('test_', SUBSTRING(username, 4)) WHERE username LIKE 'qa_%' AND role = 'TEST';
UPDATE users SET username = CONCAT('ba_', SUBSTRING(username, 7)) WHERE username LIKE 'staff_%' AND role = 'BA';
UPDATE users SET username = CONCAT('dev_', SUBSTRING(username, 7)) WHERE username LIKE 'staff_%' AND role = 'DEV';
UPDATE users SET username = CONCAT('test_', SUBSTRING(username, 7)) WHERE username LIKE 'staff_%' AND role = 'TEST';
COMMIT;
