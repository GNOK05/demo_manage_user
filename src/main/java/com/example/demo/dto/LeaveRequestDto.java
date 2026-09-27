package com.example.demo.dto;

import com.example.demo.entity.LeaveRequestStatus;
import com.example.demo.entity.LeaveDayPart;
import com.example.demo.entity.LeaveRequestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class LeaveRequestDto {
    private LeaveRequestDto() {}

    public record SaveRequest(
            @NotNull LeaveRequestType type,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            LeaveDayPart fromDayPart,
            LeaveDayPart toDayPart,
            @NotBlank @Size(max = 500) String reason
    ) {
        public SaveRequest(LeaveRequestType type, LocalDate fromDate, LocalDate toDate, String reason) {
            this(type, fromDate, toDate, LeaveDayPart.FULL_DAY, LeaveDayPart.FULL_DAY, reason);
        }
    }

    public record DecisionRequest(@NotNull LeaveRequestStatus status) {}

        public record BalanceResponse(Long userId, String userName, int year, int entitledDays,
                                                                  double usedDays, double pendingDays, double remainingDays) {}

    public record Response(
            Long id,
            Long userId,
            String userName,
            LeaveRequestType type,
            LocalDate fromDate,
            LocalDate toDate,
            LeaveDayPart fromDayPart,
            LeaveDayPart toDayPart,
            String reason,
            LeaveRequestStatus status,
            String approvedBy,
                        String createdAt,
                        String managerApprovedBy,
                        String adminApprovedBy,
                        String rejectionReason,
                        LocalDateTime cancelledAt,
                        double requestedWorkdays
    ) {}
}
