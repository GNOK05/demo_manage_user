package com.example.demo.dto;

import com.example.demo.entity.LeaveRequestStatus;
import com.example.demo.entity.WorkLocationType;
import com.example.demo.entity.LeaveDayPart;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class WorkLocationRequestDto {
    private WorkLocationRequestDto() {}

    public record SaveRequest(
            @NotNull WorkLocationType type,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @NotNull LeaveDayPart fromDayPart,
            @NotNull LeaveDayPart toDayPart,
            @Size(max = 150) String clientName,
            @Size(max = 300) String clientAddress,
            @NotBlank @Size(max = 500) String reason) {}

    public record DecisionRequest(@NotNull LeaveRequestStatus status) {}

    public record Response(Long id, Long userId, String userName, WorkLocationType type,
                           LocalDate fromDate, LocalDate toDate, LeaveDayPart fromDayPart, LeaveDayPart toDayPart,
                           double requestedWorkdays, String clientName, String clientAddress,
                           String reason, LeaveRequestStatus status, String managerApprovedBy,
                           String adminApprovedBy, LocalDateTime cancelledAt, LocalDateTime createdAt) {}
}
