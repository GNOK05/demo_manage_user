START TRANSACTION;

SET @from_date = CURRENT_DATE - INTERVAL 6 DAY;
SET @to_date = CURRENT_DATE;

INSERT IGNORE INTO attendance (
    user_id,
    attendance_date,
    check_in_time,
    check_out_time,
    status,
    note,
    created_at,
    updated_at,
    version
)
WITH RECURSIVE recent_dates AS (
    SELECT @from_date AS attendance_date
    UNION ALL
    SELECT attendance_date + INTERVAL 1 DAY
    FROM recent_dates
    WHERE attendance_date < @to_date
), generated_rows AS (
    SELECT
        u.id AS user_id,
        d.attendance_date,
        CASE
            WHEN MOD(u.id + DAYOFYEAR(d.attendance_date), 17) = 0 THEN 'LEAVE'
            WHEN MOD(u.id + DAYOFYEAR(d.attendance_date), 11) = 0 THEN 'ABSENT'
            WHEN MOD(u.id + DAYOFYEAR(d.attendance_date), 7) IN (0, 1) THEN 'LATE'
            ELSE 'PRESENT'
        END AS attendance_status,
        MOD(u.id * 7 + DAYOFYEAR(d.attendance_date), 35) AS arrival_offset,
        MOD(u.id * 11 + DAYOFYEAR(d.attendance_date), 50) AS departure_offset
    FROM users u
    CROSS JOIN recent_dates d
    WHERE u.account_status = 'ACTIVE'
)
SELECT
    user_id,
    attendance_date,
    CASE attendance_status
        WHEN 'PRESENT' THEN TIMESTAMP(attendance_date, '07:45:00') + INTERVAL arrival_offset MINUTE
        WHEN 'LATE' THEN TIMESTAMP(attendance_date, '09:05:00') + INTERVAL MOD(arrival_offset, 30) MINUTE
        ELSE NULL
    END,
    CASE
        WHEN attendance_status IN ('PRESENT', 'LATE')
            THEN TIMESTAMP(attendance_date, '17:00:00') + INTERVAL departure_offset MINUTE
        ELSE NULL
    END,
    attendance_status,
    'Dữ liệu chấm công 7 ngày gần nhất',
    TIMESTAMP(attendance_date, '18:00:00'),
    TIMESTAMP(attendance_date, '18:00:00'),
    0
FROM generated_rows;

INSERT IGNORE INTO attendance_sessions (
    attendance_id,
    check_in_time,
    check_out_time,
    created_at
)
SELECT
    a.id,
    a.check_in_time,
    a.check_out_time,
    a.check_in_time
FROM attendance a
WHERE a.attendance_date BETWEEN @from_date AND @to_date
  AND a.status IN ('PRESENT', 'LATE')
  AND a.check_in_time IS NOT NULL;

COMMIT;
