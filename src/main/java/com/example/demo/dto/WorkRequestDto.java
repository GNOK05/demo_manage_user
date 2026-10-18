package com.example.demo.dto;

import com.example.demo.entity.TaskPriority;
import com.example.demo.entity.WorkRequestHealth;
import com.example.demo.entity.WorkRequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class WorkRequestDto {
    private WorkRequestDto() {}

    public record SaveRequest(
        @NotBlank @Size(max = 180) String title,
        @Size(max = 3000) String description,
        @NotNull Long executingDepartmentId,
        Long coordinatorId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate deadline,
        TaskPriority priority,
        @Size(max = 2000) String acceptanceCriteria
    ) {}

    public record StatusRequest(@NotNull WorkRequestStatus status, @Size(max = 500) String reason) {}

    public record Response(
        Long id, String requestCode, Long projectId, String projectName,
        Long executingDepartmentId, String executingDepartmentName,
        Long coordinatorId, String coordinatorName, Long createdById,
        String title, String description, String acceptanceCriteria,
        TaskPriority priority, WorkRequestStatus status, WorkRequestHealth health,
        LocalDate startDate, LocalDate deadline,
        long totalTasks, long completedTasks, long blockedTasks, long overdueTasks, int progress
    ) {}
}
