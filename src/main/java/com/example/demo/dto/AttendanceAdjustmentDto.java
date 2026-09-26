package com.example.demo.dto;

import com.example.demo.entity.AttendanceAdjustmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.time.LocalDate;

public final class AttendanceAdjustmentDto {
    private AttendanceAdjustmentDto() {}

    public record SaveRequest(Long userId, @NotNull Long sessionId,
                              @NotNull LocalDateTime requestedCheckIn,
                              @NotNull LocalDateTime requestedCheckOut,
                              @NotBlank @Size(max = 500) String reason) {}

    public record DecisionRequest(@NotNull AttendanceAdjustmentStatus status) {}

    public record Response(Long id, Long sessionId, Long userId, String userName, String requestedBy,
                           LocalDate date, LocalDateTime originalCheckIn, LocalDateTime originalCheckOut,
                           LocalDateTime requestedCheckIn, LocalDateTime requestedCheckOut, String reason,
                           AttendanceAdjustmentStatus status, String managerApprovedBy,
                           String adminApprovedBy, LocalDateTime createdAt) {}
}