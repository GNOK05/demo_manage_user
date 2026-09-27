package com.example.demo.dto;
import com.example.demo.entity.AttendanceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
public final class AttendanceDto {
    private AttendanceDto() {}
    public record Session(Long id, LocalDateTime checkInTime, LocalDateTime checkOutTime) {}
    public record Response(Long id, Long userId, String userName, LocalDate date, LocalDateTime checkInTime,
                           LocalDateTime checkOutTime, AttendanceStatus status, List<Session> sessions) {}
    public record DepartmentSummary(LocalDate date, long totalEmployees, long present, long late,
                                    long absent, long leave) {}
}
