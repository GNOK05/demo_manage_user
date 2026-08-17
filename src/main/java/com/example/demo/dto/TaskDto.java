package com.example.demo.dto;
import com.example.demo.entity.TaskStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public final class TaskDto {
    private TaskDto() {}
    public record SaveRequest(@NotBlank String taskName, String description, @NotNull Long projectId, Long assignedToId, Long testerId,
        @NotNull LocalDate deadline, TaskStatus status) {}
    public record StatusRequest(@NotNull TaskStatus status) {}
    public record Response(Long id, String taskName, String description, Long projectId, String projectName,
        Long assignedToId, String assignedToName, Long testerId, String testerName, Long createdById, TaskStatus status, LocalDate deadline) {}
}
