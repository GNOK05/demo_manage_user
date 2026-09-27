ALTER TABLE leave_requests
  ADD COLUMN from_day_part VARCHAR(20) NOT NULL DEFAULT 'FULL_DAY' AFTER to_date,
  ADD COLUMN to_day_part VARCHAR(20) NOT NULL DEFAULT 'FULL_DAY' AFTER from_day_part,
  ADD CONSTRAINT chk_leave_day_parts CHECK (
    from_day_part IN ('FULL_DAY', 'MORNING', 'AFTERNOON')
    AND to_day_part IN ('FULL_DAY', 'MORNING', 'AFTERNOON')
  );
