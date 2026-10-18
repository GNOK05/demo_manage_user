package com.example.demo.dto;
import com.example.demo.entity.ProjectStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
public final class ProjectDto {
    private ProjectDto() {}
    public record SaveRequest(@NotBlank String projectName, @Size(max = 2000) String description, @NotNull Long departmentId,
        @NotNull LocalDate startDate, @NotNull LocalDate endDate, ProjectStatus status) {}
    public record DepartmentRef(Long id, String name, String code, String participationRole) {}
    public record Response(Long id, String projectName, String description, Long departmentId, String departmentName,
        LocalDate startDate, LocalDate endDate, ProjectStatus status, long totalTasks, long completedTasks, int progress,
        long totalRequests, long completedRequests, List<DepartmentRef> participatingDepartments) {}
}
