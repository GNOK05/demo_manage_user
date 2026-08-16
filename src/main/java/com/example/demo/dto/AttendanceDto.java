package com.example.demo.dto;
import com.example.demo.entity.AttendanceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
public final class AttendanceDto {
    private AttendanceDto() {}
    public record Response(Long id, Long userId, String userName, LocalDate date, LocalDateTime checkInTime,
                           LocalDateTime checkOutTime, AttendanceStatus status) {}
}
