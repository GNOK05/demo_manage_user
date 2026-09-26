ALTER TABLE leave_requests
  ADD COLUMN manager_approved_by_id BIGINT NULL AFTER rejection_reason,
  ADD COLUMN manager_approved_at DATETIME(6) NULL AFTER manager_approved_by_id,
  ADD COLUMN admin_approved_by_id BIGINT NULL AFTER manager_approved_at,
  ADD COLUMN admin_approved_at DATETIME(6) NULL AFTER admin_approved_by_id,
  ADD COLUMN cancelled_by_id BIGINT NULL AFTER admin_approved_at,
  ADD COLUMN cancelled_at DATETIME(6) NULL AFTER cancelled_by_id,
  ADD CONSTRAINT fk_leave_manager_approver FOREIGN KEY (manager_approved_by_id) REFERENCES users(id),
  ADD CONSTRAINT fk_leave_admin_approver FOREIGN KEY (admin_approved_by_id) REFERENCES users(id),
  ADD CONSTRAINT fk_leave_canceller FOREIGN KEY (cancelled_by_id) REFERENCES users(id);

ALTER TABLE leave_requests DROP CHECK chk_leave_type;
ALTER TABLE leave_requests ADD CONSTRAINT chk_leave_type
  CHECK (type IN ('ANNUAL', 'UNPAID', 'PERSONAL', 'SICK', 'MATERNITY'));
ALTER TABLE leave_requests DROP CHECK chk_leave_status;
ALTER TABLE leave_requests ADD CONSTRAINT chk_leave_status
  CHECK (status IN ('PENDING', 'PENDING_MANAGER', 'PENDING_ADMIN', 'APPROVED', 'REJECTED', 'REJECTED_MANAGER', 'REJECTED_ADMIN', 'CANCELLED'));

CREATE TABLE annual_leave_balances (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  leave_year INT NOT NULL,
  entitled_days INT NOT NULL DEFAULT 12,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_annual_leave_balance_user_year (user_id, leave_year),
  CONSTRAINT fk_annual_leave_balance_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT chk_annual_leave_balance_days CHECK (entitled_days BETWEEN 0 AND 366)
) ENGINE=InnoDB;

UPDATE leave_requests lr
JOIN users approver ON approver.id = lr.approved_by_id
SET lr.admin_approved_by_id = approver.id,
    lr.admin_approved_at = COALESCE(lr.approved_at, lr.updated_at)
WHERE lr.status = 'APPROVED' AND approver.role = 'ADMIN';

UPDATE leave_requests lr
JOIN users approver ON approver.id = lr.approved_by_id
SET lr.manager_approved_by_id = approver.id,
    lr.manager_approved_at = COALESCE(lr.approved_at, lr.updated_at)
WHERE lr.status = 'APPROVED' AND approver.role = 'MANAGER';