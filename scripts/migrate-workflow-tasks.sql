-- Run once on an existing MySQL database before starting the updated backend.
ALTER TABLE tasks
    ADD COLUMN task_type VARCHAR(20) NOT NULL DEFAULT 'DEVELOPMENT',
    ADD COLUMN related_task_id BIGINT NULL,
    ADD COLUMN result_summary VARCHAR(3000) NULL,
    ADD CONSTRAINT fk_tasks_related_task FOREIGN KEY (related_task_id) REFERENCES tasks(id);
UPDATE tasks SET task_type = 'TESTING' WHERE status = 'TESTING';

CREATE TABLE work_request_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    work_request_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NULL,
    changed_at DATETIME(6) NOT NULL,
    INDEX idx_work_request_history_request (work_request_id, changed_at),
    CONSTRAINT fk_history_request FOREIGN KEY (work_request_id) REFERENCES work_requests(id),
    CONSTRAINT fk_history_actor FOREIGN KEY (actor_id) REFERENCES users(id)
);
