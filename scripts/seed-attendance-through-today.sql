-- Idempotently fill missing demo attendance from 2026-11-03 through today.
-- Weekdays use the regular schedule; 2026-11-07 has a small Saturday shift.
-- Existing attendance and sessions are never changed.
START TRANSACTION;

INSERT INTO attendance (
    user_id, attendance_date, check_in_time, check_out_time,
    status, note, created_at, updated_at, version
)
WITH RECURSIVE dates AS (
    SELECT DATE('2026-11-03') AS work_date
    UNION ALL
    SELECT work_date + INTERVAL 1 DAY FROM dates WHERE work_date < CURRENT_DATE()
), rows_to_add AS (
    SELECT u.id AS user_id, d.work_date,
           CASE
               WHEN EXISTS (
                   SELECT 1 FROM leave_requests l
                   WHERE l.user_id = u.id AND l.status = 'APPROVED'
                     AND d.work_date BETWEEN l.from_date AND l.to_date
               ) THEN 'LEAVE'
               WHEN MOD(u.id + DAYOFYEAR(d.work_date), 19) = 0 THEN 'ABSENT'
               WHEN MOD(u.id + DAYOFYEAR(d.work_date), 7) IN (0, 1) THEN 'LATE'
               ELSE 'PRESENT'
           END AS day_status
    FROM users u CROSS JOIN dates d
    WHERE u.account_status = 'ACTIVE'
      AND WEEKDAY(d.work_date) < 5
      AND NOT EXISTS (
          SELECT 1 FROM attendance a
          WHERE a.user_id = u.id AND a.attendance_date = d.work_date
      )
)
SELECT user_id, work_date,
       CASE day_status
           WHEN 'PRESENT' THEN TIMESTAMP(work_date, '07:45:00') + INTERVAL MOD(user_id * 7 + DAYOFYEAR(work_date), 40) MINUTE
           WHEN 'LATE' THEN TIMESTAMP(work_date, '09:05:00') + INTERVAL MOD(user_id * 7 + DAYOFYEAR(work_date), 35) MINUTE
           ELSE NULL
       END,
       CASE WHEN day_status IN ('PRESENT', 'LATE') AND
                 (work_date < CURRENT_DATE() OR CURRENT_TIME() >= '17:30:00')
            THEN TIMESTAMP(work_date, '16:50:00') + INTERVAL MOD(user_id * 11 + DAYOFYEAR(work_date), 40) MINUTE
            ELSE NULL END,
       day_status, 'Dữ liệu chấm công mô phỏng',
       CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 0
FROM rows_to_add;

INSERT INTO attendance (
    user_id, attendance_date, check_in_time, check_out_time,
    status, note, created_at, updated_at, version
)
WITH ranked AS (
    SELECT u.id AS user_id, u.role,
           ROW_NUMBER() OVER (PARTITION BY u.role ORDER BY MD5(CONCAT(u.id, ':2026-11-07'))) AS role_rank
    FROM users u
    WHERE u.account_status = 'ACTIVE'
      AND u.role IN ('DEV', 'TEST', 'BA', 'TEAM_LEAD')
      AND NOT EXISTS (
          SELECT 1 FROM leave_requests l
          WHERE l.user_id = u.id AND l.status = 'APPROVED'
            AND DATE('2026-11-07') BETWEEN l.from_date AND l.to_date
      )
), weekend_shift AS (
    SELECT user_id, MOD(user_id + 311, 6) = 0 AS is_late
    FROM ranked
    WHERE (role = 'DEV' AND role_rank <= 16)
       OR (role = 'TEST' AND role_rank <= 5)
       OR (role = 'BA' AND role_rank <= 2)
       OR (role = 'TEAM_LEAD' AND role_rank <= 1)
)
SELECT w.user_id, DATE('2026-11-07'),
       CASE WHEN w.is_late THEN TIMESTAMP('2026-11-07', '09:05:00') + INTERVAL MOD(w.user_id * 7, 20) MINUTE
            ELSE TIMESTAMP('2026-11-07', '08:00:00') + INTERVAL MOD(w.user_id * 7, 25) MINUTE END,
       TIMESTAMP('2026-11-07', '12:10:00') + INTERVAL MOD(w.user_id * 11, 30) MINUTE,
       CASE WHEN w.is_late THEN 'LATE' ELSE 'PRESENT' END,
       'Dữ liệu mô phỏng ca làm thứ Bảy',
       CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 0
FROM weekend_shift w
WHERE CURRENT_DATE() >= DATE('2026-11-07')
  AND NOT EXISTS (
      SELECT 1 FROM attendance a
      WHERE a.user_id = w.user_id AND a.attendance_date = DATE('2026-11-07')
  );

INSERT INTO attendance_sessions (attendance_id, check_in_time, check_out_time, created_at)
SELECT a.id, a.check_in_time, a.check_out_time, a.check_in_time
FROM attendance a
WHERE a.attendance_date BETWEEN '2026-11-03' AND CURRENT_DATE()
  AND a.check_in_time IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM attendance_sessions s WHERE s.attendance_id = a.id);

COMMIT;
