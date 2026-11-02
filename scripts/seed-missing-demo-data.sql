START TRANSACTION;

-- Every active user gets a current-year annual-leave balance.
INSERT INTO annual_leave_balances (user_id, leave_year, entitled_days, created_at, updated_at, version)
SELECT u.id, YEAR(CURRENT_DATE), 15, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0
FROM users u
WHERE u.account_status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM annual_leave_balances b
      WHERE b.user_id = u.id
        AND b.leave_year = YEAR(CURRENT_DATE)
  );

-- Payroll periods for the current year: closed through the previous month,
-- with the current month open for processing.
INSERT IGNORE INTO payroll_periods
    (period_year, period_month, is_closed, closed_at, closed_by_id, version)
SELECT YEAR(CURRENT_DATE), months.period_month,
       IF(months.period_month < MONTH(CURRENT_DATE), 1, 0),
       IF(months.period_month < MONTH(CURRENT_DATE),
          TIMESTAMP(
              STR_TO_DATE(CONCAT(YEAR(CURRENT_DATE), '-', LPAD(months.period_month, 2, '0'), '-01'), '%Y-%m-%d'),
              '18:00:00'
          ), NULL),
       IF(months.period_month < MONTH(CURRENT_DATE), 1, NULL),
       0
FROM (
    SELECT 1 AS period_month UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
    UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
) months;

-- Representative recent work-location requests for the request/approval screens.
INSERT INTO work_location_requests
    (user_id, type, from_date, to_date, from_day_part, to_day_part,
     client_name, client_address, reason, status,
     manager_approved_by_id, manager_approved_at,
     admin_approved_by_id, admin_approved_at, version)
SELECT x.user_id, x.request_type, x.from_date, x.to_date, x.from_part, x.to_part,
       x.client_name, x.client_address, x.reason, x.request_status,
       x.manager_id,
       IF(x.request_status = 'APPROVED', CURRENT_TIMESTAMP(6), NULL),
       IF(x.request_status = 'APPROVED', 1, NULL),
       IF(x.request_status = 'APPROVED', CURRENT_TIMESTAMP(6), NULL),
       0
FROM (
    SELECT 5 AS user_id, 'WORK_FROM_HOME' AS request_type,
           CURRENT_DATE + INTERVAL 1 DAY AS from_date,
           CURRENT_DATE + INTERVAL 2 DAY AS to_date,
           'MORNING' AS from_part, 'AFTERNOON' AS to_part,
           NULL AS client_name, NULL AS client_address,
           'Tập trung xử lý tài liệu và công việc cần thời gian yên tĩnh' AS reason,
           'PENDING_MANAGER' AS request_status, 4 AS manager_id,
           'member_1_1' AS request_key
    UNION ALL
    SELECT 12, 'CLIENT_SITE', CURRENT_DATE - INTERVAL 6 DAY, CURRENT_DATE - INTERVAL 5 DAY,
           'MORNING', 'AFTERNOON', 'Công ty ABC', 'Tòa nhà A, Quận 1',
           'Hỗ trợ triển khai và nghiệm thu tại khách hàng', 'APPROVED', 11, 'member_2_1'
    UNION ALL
    SELECT 7, 'WORK_FROM_HOME', CURRENT_DATE + INTERVAL 3 DAY, CURRENT_DATE + INTERVAL 3 DAY,
           'MORNING', 'AFTERNOON', NULL, NULL,
           'Làm việc từ xa theo kế hoạch cá nhân', 'REJECTED_MANAGER', 4, 'qa_1_1'
    UNION ALL
    SELECT 4, 'WORK_FROM_HOME', CURRENT_DATE + INTERVAL 8 DAY, CURRENT_DATE + INTERVAL 10 DAY,
           'MORNING', 'AFTERNOON', NULL, NULL,
           'Chuẩn bị kế hoạch sprint và tài liệu bàn giao', 'APPROVED', 4, 'lead_1'
    UNION ALL
    SELECT 3, 'CLIENT_SITE', CURRENT_DATE - INTERVAL 12 DAY, CURRENT_DATE - INTERVAL 11 DAY,
           'MORNING', 'AFTERNOON', 'Đối tác XYZ', 'Khu công nghệ cao, TP. Thủ Đức',
           'Khảo sát yêu cầu và họp khởi động dự án', 'APPROVED', 4, 'pm_1'
    UNION ALL
    SELECT 9, 'WORK_FROM_HOME', CURRENT_DATE + INTERVAL 5 DAY, CURRENT_DATE + INTERVAL 5 DAY,
           'MORNING', 'AFTERNOON', NULL, NULL,
           'Đề nghị làm việc tại nhà', 'CANCELLED', 4, 'viewer_1'
) x
WHERE NOT EXISTS (
    SELECT 1
    FROM work_location_requests existing
    WHERE existing.user_id = x.user_id
      AND existing.reason = x.reason
      AND existing.from_date = x.from_date
      AND existing.to_date = x.to_date
);

COMMIT;
