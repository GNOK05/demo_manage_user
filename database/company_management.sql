CREATE DATABASE IF NOT EXISTS company_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE company_management;

CREATE TABLE departments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL UNIQUE,
  code VARCHAR(30) NOT NULL UNIQUE,
  description VARCHAR(1000),
  manager_id BIGINT NULL
);

CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(80) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  phone VARCHAR(30),
  role VARCHAR(20) NOT NULL,
  job_title VARCHAR(50),
  department_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES departments(id)
);

ALTER TABLE departments ADD CONSTRAINT fk_department_manager FOREIGN KEY (manager_id) REFERENCES users(id);

CREATE TABLE attendance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  attendance_date DATE NOT NULL,
  check_in_time DATETIME NULL,
  check_out_time DATETIME NULL,
  status VARCHAR(20) NOT NULL,
  CONSTRAINT uq_attendance_user_date UNIQUE (user_id, attendance_date),
  CONSTRAINT fk_attendance_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE projects (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_name VARCHAR(160) NOT NULL,
  description VARCHAR(2000),
  department_id BIGINT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL,
  CONSTRAINT fk_project_department FOREIGN KEY (department_id) REFERENCES departments(id)
);

CREATE TABLE tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_name VARCHAR(160) NOT NULL,
  description VARCHAR(2000),
  project_id BIGINT NOT NULL,
  assigned_to_id BIGINT NULL,
  tester_id BIGINT NULL,
  created_by_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  deadline DATE NOT NULL,
  CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES projects(id),
  CONSTRAINT fk_task_assignee FOREIGN KEY (assigned_to_id) REFERENCES users(id),
  CONSTRAINT fk_task_tester FOREIGN KEY (tester_id) REFERENCES users(id),
  CONSTRAINT fk_task_creator FOREIGN KEY (created_by_id) REFERENCES users(id)
);

CREATE INDEX idx_attendance_user ON attendance(user_id);
CREATE INDEX idx_project_department ON projects(department_id);
CREATE INDEX idx_task_project ON tasks(project_id);
CREATE INDEX idx_task_assignee ON tasks(assigned_to_id);
CREATE INDEX idx_task_tester ON tasks(tester_id);
