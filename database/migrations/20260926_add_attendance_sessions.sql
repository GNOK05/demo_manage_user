CREATE TABLE IF NOT EXISTS attendance_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  attendance_id BIGINT NOT NULL,
  check_in_time DATETIME(6) NOT NULL,
  check_out_time DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_attendance_session_parent_time (attendance_id, check_in_time),
  CONSTRAINT fk_attendance_session_attendance FOREIGN KEY (attendance_id) REFERENCES attendance(id),
  CONSTRAINT chk_attendance_session_times CHECK (check_out_time IS NULL OR check_out_time >= check_in_time)
) ENGINE=InnoDB;

INSERT INTO attendance_sessions (attendance_id, check_in_time, check_out_time)
SELECT a.id, a.check_in_time, a.check_out_time
FROM attendance a
WHERE a.check_in_time IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM attendance_sessions s WHERE s.attendance_id = a.id
  );