CREATE TABLE payroll_periods (
  id BIGINT NOT NULL AUTO_INCREMENT,
  period_year INT NOT NULL,
  period_month INT NOT NULL,
  is_closed BOOLEAN NOT NULL DEFAULT TRUE,
  closed_at DATETIME(6) NULL,
  closed_by_id BIGINT NULL,
  reopened_at DATETIME(6) NULL,
  reopened_by_id BIGINT NULL,
  reopen_reason VARCHAR(500) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payroll_period_year_month (period_year, period_month),
  CONSTRAINT fk_payroll_period_closed_by FOREIGN KEY (closed_by_id) REFERENCES users(id),
  CONSTRAINT fk_payroll_period_reopened_by FOREIGN KEY (reopened_by_id) REFERENCES users(id),
  CONSTRAINT chk_payroll_period_month CHECK (period_month BETWEEN 1 AND 12)
) ENGINE=InnoDB;