START TRANSACTION;

-- Fill the missing recent calendar days without touching existing records.
SET @attendance_from = '2026-10-19';
SET @attendance_to = CURRENT_DATE;

INSERT IGNORE INTO attendance (
    user_id, attendance_date, check_in_time, check_out_time, status,
    note, created_at, updated_at, version
)
WITH RECURSIVE recent_dates AS (
    SELECT CAST(@attendance_from AS DATE) AS attendance_date
    UNION ALL
    SELECT attendance_date + INTERVAL 1 DAY
    FROM recent_dates
    WHERE attendance_date < @attendance_to
), generated_rows AS (
    SELECT
        u.id AS user_id,
        d.attendance_date,
        CASE
            WHEN WEEKDAY(d.attendance_date) >= 5 THEN
                CASE
                    WHEN MOD(u.id + DAYOFYEAR(d.attendance_date), 5) = 0 THEN 'PRESENT'
                    WHEN MOD(u.id + DAYOFYEAR(d.attendance_date), 7) = 0 THEN 'LATE'
                    WHEN MOD(u.id + DAYOFYEAR(d.attendance_date), 3) = 0 THEN 'LEAVE'
                    ELSE 'ABSENT'
                END
            WHEN MOD(u.id + DAYOFYEAR(d.attendance_date), 17) = 0 THEN 'LEAVE'
            WHEN MOD(u.id + DAYOFYEAR(d.attendance_date), 13) = 0 THEN 'ABSENT'
            WHEN MOD(u.id + DAYOFYEAR(d.attendance_date), 7) IN (0, 1, 2) THEN 'LATE'
            ELSE 'PRESENT'
        END AS attendance_status,
        MOD(u.id * 7 + DAYOFYEAR(d.attendance_date), 41) AS arrival_offset,
        MOD(u.id * 11 + DAYOFYEAR(d.attendance_date), 53) AS departure_offset
    FROM users u
    CROSS JOIN recent_dates d
    WHERE u.account_status = 'ACTIVE'
)
SELECT
    user_id,
    attendance_date,
    CASE attendance_status
        WHEN 'PRESENT' THEN TIMESTAMP(attendance_date, '07:45:00') + INTERVAL arrival_offset MINUTE
        WHEN 'LATE' THEN TIMESTAMP(attendance_date, '09:05:00') + INTERVAL MOD(arrival_offset, 35) MINUTE
        ELSE NULL
    END,
    CASE
        WHEN attendance_status IN ('PRESENT', 'LATE') AND attendance_date < CURRENT_DATE
            THEN TIMESTAMP(attendance_date, '16:45:00') + INTERVAL departure_offset MINUTE
        ELSE NULL
    END,
    attendance_status,
    'Ghi nhận chấm công tự động từ máy chấm công',
    TIMESTAMP(attendance_date, '18:00:00'),
    TIMESTAMP(attendance_date, '18:00:00'),
    0
FROM generated_rows;

INSERT IGNORE INTO attendance_sessions (attendance_id, check_in_time, check_out_time, created_at)
SELECT a.id, a.check_in_time, a.check_out_time, a.check_in_time
FROM attendance a
WHERE a.attendance_date BETWEEN @attendance_from AND @attendance_to
  AND a.status IN ('PRESENT', 'LATE')
  AND a.check_in_time IS NOT NULL;

-- Keep only six realistic, recent leave requests; remove stale/repeated seed requests.
DELETE FROM leave_requests;

INSERT INTO leave_requests (
    user_id, type, from_date, to_date, from_day_part, to_day_part, reason, status,
    approved_by_id, approved_at, rejection_reason,
    manager_approved_by_id, manager_approved_at,
    admin_approved_by_id, admin_approved_at,
    cancelled_by_id, cancelled_at, created_at, updated_at, version
)
SELECT
    u.id, x.leave_type, x.from_date, x.to_date, x.from_part, x.to_part,
    x.reason, x.request_status,
    CASE WHEN x.request_status = 'APPROVED' THEN x.manager_id ELSE NULL END,
    CASE WHEN x.request_status = 'APPROVED' THEN CURRENT_TIMESTAMP(6) ELSE NULL END,
    CASE WHEN x.request_status = 'REJECTED_MANAGER' THEN 'Thời gian nghỉ chưa phù hợp với kế hoạch nhóm' ELSE NULL END,
    CASE WHEN x.request_status IN ('APPROVED', 'REJECTED_MANAGER') THEN x.manager_id ELSE NULL END,
    CASE WHEN x.request_status IN ('APPROVED', 'REJECTED_MANAGER') THEN CURRENT_TIMESTAMP(6) ELSE NULL END,
    CASE WHEN x.request_status = 'APPROVED' THEN 1 ELSE NULL END,
    CASE WHEN x.request_status = 'APPROVED' THEN CURRENT_TIMESTAMP(6) ELSE NULL END,
    CASE WHEN x.request_status = 'CANCELLED' THEN 1 ELSE NULL END,
    CASE WHEN x.request_status = 'CANCELLED' THEN CURRENT_TIMESTAMP(6) ELSE NULL END,
    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0
FROM (
    SELECT 'member_1_1' AS username, 'ANNUAL' AS leave_type,
           CURRENT_DATE + INTERVAL 3 DAY AS from_date, CURRENT_DATE + INTERVAL 4 DAY AS to_date,
           'FULL_DAY' AS from_part, 'FULL_DAY' AS to_part,
           'Nghỉ phép gia đình đã lên kế hoạch trước' AS reason,
           'PENDING_MANAGER' AS request_status, 4 AS manager_id
    UNION ALL
    SELECT 'member_2_1', 'ANNUAL', CURRENT_DATE + INTERVAL 5 DAY, CURRENT_DATE + INTERVAL 5 DAY,
           'FULL_DAY', 'FULL_DAY', 'Giải quyết việc cá nhân', 'APPROVED', 11
    UNION ALL
    SELECT 'qa_1_1', 'ANNUAL', CURRENT_DATE - INTERVAL 4 DAY, CURRENT_DATE - INTERVAL 3 DAY,
           'FULL_DAY', 'FULL_DAY', 'Nghỉ phép đã được phê duyệt trong tháng này', 'APPROVED', 4
    UNION ALL
    SELECT 'member_3_1', 'UNPAID', CURRENT_DATE + INTERVAL 8 DAY, CURRENT_DATE + INTERVAL 8 DAY,
           'AFTERNOON', 'AFTERNOON', 'Giải quyết thủ tục hành chính', 'PENDING_ADMIN', 18
    UNION ALL
    SELECT 'member_4_1', 'ANNUAL', CURRENT_DATE + INTERVAL 1 DAY, CURRENT_DATE + INTERVAL 1 DAY,
           'FULL_DAY', 'FULL_DAY', 'Đề nghị nghỉ phép ngắn ngày', 'REJECTED_MANAGER', 25
    UNION ALL
    SELECT 'member_5_1', 'ANNUAL', CURRENT_DATE + INTERVAL 6 DAY, CURRENT_DATE + INTERVAL 7 DAY,
           'FULL_DAY', 'FULL_DAY', 'Nghỉ phép cá nhân', 'CANCELLED', 32
) x
JOIN users u ON u.username = x.username;

COMMIT;
