package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.LeaveRequestDto;
import com.example.demo.entity.LeaveRequestStatus;
import com.example.demo.service.LeaveRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {
    private final LeaveRequestService service;

    @GetMapping("/my")
    public ApiResponse<List<LeaveRequestDto.Response>> my() {
        return ApiResponse.ok(service.myRequests());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<List<LeaveRequestDto.Response>> pending() {
        return ApiResponse.ok(service.pendingForApproval());
    }

    @PostMapping
    public ApiResponse<LeaveRequestDto.Response> create(@Valid @RequestBody LeaveRequestDto.SaveRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<LeaveRequestDto.Response> approve(@PathVariable Long id, @RequestBody LeaveRequestStatus status) {
        return ApiResponse.ok(service.approve(id, status));
    }
}
