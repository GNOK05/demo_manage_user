ALTER TABLE tasks
  ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  ADD COLUMN start_date DATE NULL,
  ADD COLUMN estimated_hours INT NULL,
  ADD COLUMN acceptance_criteria VARCHAR(2000) NULL,
  ADD COLUMN blocked_reason VARCHAR(500) NULL;

UPDATE tasks t
JOIN projects p ON p.id = t.project_id
SET t.start_date = LEAST(p.start_date, t.deadline);

ALTER TABLE tasks
  MODIFY COLUMN start_date DATE NOT NULL,
  DROP CHECK chk_task_status,
  ADD CONSTRAINT chk_task_status CHECK (status IN ('BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'TESTING', 'BLOCKED', 'DONE', 'CANCELLED')),
  ADD CONSTRAINT chk_task_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
  ADD CONSTRAINT chk_task_dates CHECK (deadline >= start_date),
  ADD CONSTRAINT chk_task_estimated_hours CHECK (estimated_hours IS NULL OR estimated_hours BETWEEN 1 AND 1000);
