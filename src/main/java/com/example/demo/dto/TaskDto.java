package com.example.demo.dto;
import com.example.demo.entity.TaskStatus;
import com.example.demo.entity.TaskPriority;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public final class TaskDto {
    private TaskDto() {}
    public record SaveRequest(@NotBlank String taskName, @Size(max = 2000) String description, @NotNull Long projectId, Long assignedToId, Long testerId,
        @NotNull LocalDate deadline, TaskStatus status, TaskPriority priority, LocalDate startDate,
        @Min(1) @Max(1000) Integer estimatedHours, @Size(max = 2000) String acceptanceCriteria,
        @Size(max = 500) String blockedReason) {}
    public record StatusRequest(@NotNull TaskStatus status, @Size(max = 500) String blockedReason) {}
    public record Response(Long id, String taskName, String description, Long projectId, String projectName,
        Long assignedToId, String assignedToName, Long testerId, String testerName, Long createdById,
        TaskStatus status, LocalDate startDate, LocalDate deadline, TaskPriority priority,
        Integer estimatedHours, String acceptanceCriteria, String blockedReason) {}
}
