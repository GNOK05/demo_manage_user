-- CompanyHub production-oriented MySQL schema.
-- Run this file on a new database. Existing data should be migrated and backed up first.
CREATE DATABASE IF NOT EXISTS company_management
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE company_management;

CREATE TABLE departments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  code VARCHAR(30) NOT NULL,
  description VARCHAR(1000) NULL,
  manager_id BIGINT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_department_name (name),
  UNIQUE KEY uk_department_code (code),
  UNIQUE KEY uk_department_manager (manager_id),
  KEY idx_department_active (is_active)
) ENGINE=InnoDB;

CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(80) NOT NULL,
  password VARCHAR(255) NOT NULL,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(150) NOT NULL,
  phone VARCHAR(30) NULL,
  role VARCHAR(20) NOT NULL,
  job_title VARCHAR(80) NULL,
  department_id BIGINT NULL,
  account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  last_login_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  UNIQUE KEY uk_user_email (email),
  KEY idx_user_department_status (department_id, account_status),
  KEY idx_user_role (role),
  CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES departments(id),
  CONSTRAINT chk_user_role CHECK (role IN ('ADMIN', 'MANAGER', 'EMPLOYEE')),
  CONSTRAINT chk_user_account_status CHECK (account_status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
) ENGINE=InnoDB;

ALTER TABLE departments
  ADD CONSTRAINT fk_department_manager FOREIGN KEY (manager_id) REFERENCES users(id);

CREATE TABLE attendance (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  attendance_date DATE NOT NULL,
  check_in_time DATETIME(6) NULL,
  check_out_time DATETIME(6) NULL,
  status VARCHAR(20) NOT NULL,
  note VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_attendance_user_date (user_id, attendance_date),
  KEY idx_attendance_date_status (attendance_date, status),
  CONSTRAINT fk_attendance_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT chk_attendance_status CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'LEAVE')),
  CONSTRAINT chk_attendance_times CHECK (check_out_time IS NULL OR check_in_time IS NULL OR check_out_time >= check_in_time)
) ENGINE=InnoDB;

CREATE TABLE projects (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_name VARCHAR(160) NOT NULL,
  description VARCHAR(2000) NULL,
  department_id BIGINT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
  created_by_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_department_name (department_id, project_name),
  KEY idx_project_status_dates (status, start_date, end_date),
  CONSTRAINT fk_project_department FOREIGN KEY (department_id) REFERENCES departments(id),
  CONSTRAINT fk_project_creator FOREIGN KEY (created_by_id) REFERENCES users(id),
  CONSTRAINT chk_project_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'ON_HOLD')),
  CONSTRAINT chk_project_dates CHECK (end_date >= start_date)
) ENGINE=InnoDB;

CREATE TABLE tasks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_name VARCHAR(160) NOT NULL,
  description VARCHAR(2000) NULL,
  project_id BIGINT NOT NULL,
  assigned_to_id BIGINT NULL,
  tester_id BIGINT NULL,
  created_by_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'TODO',
  deadline DATE NOT NULL,
  completed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_task_project_status (project_id, status),
  KEY idx_task_assignee_deadline (assigned_to_id, deadline),
  KEY idx_task_tester (tester_id),
  KEY idx_task_creator (created_by_id),
  CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES projects(id),
  CONSTRAINT fk_task_assignee FOREIGN KEY (assigned_to_id) REFERENCES users(id),
  CONSTRAINT fk_task_tester FOREIGN KEY (tester_id) REFERENCES users(id),
  CONSTRAINT fk_task_creator FOREIGN KEY (created_by_id) REFERENCES users(id),
  CONSTRAINT chk_task_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'REVIEW'))
) ENGINE=InnoDB;

CREATE TABLE leave_requests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL,
  from_date DATE NOT NULL,
  to_date DATE NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  approved_by_id BIGINT NULL,
  approved_at DATETIME(6) NULL,
  rejection_reason VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_leave_user_dates (user_id, from_date, to_date),
  KEY idx_leave_approval_queue (status, created_at),
  KEY idx_leave_approver (approved_by_id),
  CONSTRAINT fk_leave_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_leave_approver FOREIGN KEY (approved_by_id) REFERENCES users(id),
  CONSTRAINT chk_leave_type CHECK (type IN ('ANNUAL', 'PERSONAL', 'SICK', 'MATERNITY')),
  CONSTRAINT chk_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  CONSTRAINT chk_leave_dates CHECK (to_date >= from_date)
) ENGINE=InnoDB;

CREATE TABLE notification_reads (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  notification_key VARCHAR(120) NOT NULL,
  read_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_read (user_id, notification_key),
  KEY idx_notification_reads_user (user_id, read_at),
  CONSTRAINT fk_notification_read_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;
