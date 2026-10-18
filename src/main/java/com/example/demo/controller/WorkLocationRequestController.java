package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.WorkLocationRequestDto;
import com.example.demo.service.WorkLocationRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/work-location-requests")
@RequiredArgsConstructor
public class WorkLocationRequestController {
    private final WorkLocationRequestService service;

    @GetMapping("/my") public ApiResponse<List<WorkLocationRequestDto.Response>> my() { return ApiResponse.ok(service.myRequests()); }
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')")
    public ApiResponse<List<WorkLocationRequestDto.Response>> pending() { return ApiResponse.ok(service.pendingForApproval()); }
    @PostMapping public ApiResponse<WorkLocationRequestDto.Response> create(@Valid @RequestBody WorkLocationRequestDto.SaveRequest request) { return ApiResponse.ok(service.create(request)); }
    @PatchMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')")
    public ApiResponse<WorkLocationRequestDto.Response> decide(@PathVariable Long id, @Valid @RequestBody WorkLocationRequestDto.DecisionRequest request) { return ApiResponse.ok(service.decide(id, request.status())); }
    @PostMapping("/{id}/cancel") public ApiResponse<WorkLocationRequestDto.Response> cancel(@PathVariable Long id) { return ApiResponse.ok(service.cancel(id)); }
}
