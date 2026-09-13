package com.example.demo.dto;

import java.util.Map;

public final class DashboardDto {
    private DashboardDto() {}

    public record Summary(
            long totalEmployees,
            long totalDepartments,
            long totalProjects,
            long activeProjects,
            long completedProjects,
            long totalTasks,
            long pendingTasks,
            long completedTasks,
            long overdueTasks,
            Map<String, Long> attendanceToday,
            long pendingLeaveRequests
    ) {}
}
