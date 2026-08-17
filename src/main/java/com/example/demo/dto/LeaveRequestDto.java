package com.example.demo.dto;

import com.example.demo.entity.LeaveRequestStatus;
import com.example.demo.entity.LeaveRequestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class LeaveRequestDto {
    private LeaveRequestDto() {}

    public record SaveRequest(
            @NotNull LeaveRequestType type,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @NotBlank @Size(max = 500) String reason
    ) {}

    public record Response(
            Long id,
            Long userId,
            String userName,
            LeaveRequestType type,
            LocalDate fromDate,
            LocalDate toDate,
            String reason,
            LeaveRequestStatus status,
            String approvedBy,
            String createdAt
    ) {}
}
