package com.example.demo.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class PayrollDto {
    private PayrollDto() {}

    public record EmployeeRow(Long userId, String username, String fullName, String department,
                              int workDays, int lateDays, int absentDays, double workedHours,
                              int annualLeaveDays, int unpaidLeaveDays, int otherLeaveDays,
                              int payrollWorkDays) {}

    public record MonthlyReport(int year, int month, boolean closed, LocalDateTime closedAt,
                                String closedBy, LocalDateTime reopenedAt, String reopenedBy,
                                String reopenReason, List<EmployeeRow> employees) {}

    public record ReopenRequest(String reason) {}
}