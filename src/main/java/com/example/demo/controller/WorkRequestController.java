package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.WorkRequestDto;
import com.example.demo.entity.TaskPriority;
import com.example.demo.entity.WorkRequestHealth;
import com.example.demo.entity.WorkRequestStatus;
import com.example.demo.service.WorkRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkRequestController {
    private final WorkRequestService service;

    @GetMapping("/work-requests")
    public ApiResponse<List<WorkRequestDto.Response>> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) WorkRequestStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) WorkRequestHealth health,
            @RequestParam(defaultValue = "false") boolean overdue) {
        return ApiResponse.ok(service.search(keyword, projectId, departmentId, status, priority, health, overdue));
    }

    @GetMapping("/projects/{projectId}/work-requests")
    public ApiResponse<List<WorkRequestDto.Response>> byProject(@PathVariable Long projectId) {
        return ApiResponse.ok(service.byProject(projectId));
    }

    @GetMapping("/work-requests/{id}")
    public ApiResponse<WorkRequestDto.Response> get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }

    @PostMapping("/projects/{projectId}/work-requests")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')")
    public ApiResponse<WorkRequestDto.Response> create(@PathVariable Long projectId, @Valid @RequestBody WorkRequestDto.SaveRequest request) {
        return ApiResponse.ok(service.create(projectId, request));
    }

    @PutMapping("/work-requests/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')")
    public ApiResponse<WorkRequestDto.Response> update(@PathVariable Long id, @Valid @RequestBody WorkRequestDto.SaveRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PatchMapping("/work-requests/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')")
    public ApiResponse<WorkRequestDto.Response> status(@PathVariable Long id, @Valid @RequestBody WorkRequestDto.StatusRequest request) {
        return ApiResponse.ok(service.updateStatus(id, request));
    }

    @DeleteMapping("/work-requests/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')")
    public void delete(@PathVariable Long id) { service.delete(id); }
}
