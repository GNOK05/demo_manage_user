START TRANSACTION;

UPDATE projects SET description = CASE id
    WHEN 1 THEN 'Nền tảng thương mại điện tử cấp công ty.'
    WHEN 2 THEN 'Hệ thống hoạch định nguồn lực doanh nghiệp.'
    WHEN 3 THEN 'Hệ thống quản lý quan hệ khách hàng.'
    WHEN 4 THEN 'Ứng dụng di động phục vụ khách hàng và nhân viên.'
    WHEN 5 THEN 'Cổng thông tin nội bộ toàn công ty.'
END
WHERE id IN (1, 2, 3, 4, 5);

UPDATE leave_requests SET reason = CASE id
    WHEN 46 THEN 'Nghỉ phép gia đình đã lên kế hoạch trước'
    WHEN 47 THEN 'Giải quyết việc cá nhân'
    WHEN 48 THEN 'Nghỉ phép đã được phê duyệt trong tháng này'
    WHEN 49 THEN 'Giải quyết thủ tục hành chính'
    WHEN 50 THEN 'Đề nghị nghỉ phép ngắn ngày'
    WHEN 51 THEN 'Nghỉ phép cá nhân'
END
WHERE id IN (46, 47, 48, 49, 50, 51);

UPDATE work_location_requests SET
    reason = CASE id
        WHEN 1 THEN 'Tập trung xử lý tài liệu và công việc cần thời gian yên tĩnh'
        WHEN 2 THEN 'Hỗ trợ triển khai và nghiệm thu tại khách hàng'
        WHEN 3 THEN 'Làm việc từ xa theo kế hoạch cá nhân'
        WHEN 4 THEN 'Chuẩn bị kế hoạch sprint và tài liệu bàn giao'
        WHEN 5 THEN 'Khảo sát yêu cầu và họp khởi động dự án'
        WHEN 6 THEN 'Đề nghị làm việc tại nhà'
    END,
    client_name = CASE id
        WHEN 2 THEN 'Công ty ABC'
        WHEN 5 THEN 'Đối tác XYZ'
        ELSE client_name
    END,
    client_address = CASE id
        WHEN 2 THEN 'Tòa nhà A, Quận 1'
        WHEN 5 THEN 'Khu công nghệ cao, Thành phố Thủ Đức'
        ELSE client_address
    END
WHERE id IN (1, 2, 3, 4, 5, 6);

COMMIT;
