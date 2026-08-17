package com.example.demo.dto;
import com.example.demo.entity.ProjectStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public final class ProjectDto {
    private ProjectDto() {}
    public record SaveRequest(@NotBlank String projectName, @Size(max = 2000) String description, @NotNull Long departmentId,
        @NotNull LocalDate startDate, @NotNull LocalDate endDate, ProjectStatus status) {}
    public record Response(Long id, String projectName, String description, Long departmentId, String departmentName,
        LocalDate startDate, LocalDate endDate, ProjectStatus status, long totalTasks, long completedTasks, int progress) {}
}
