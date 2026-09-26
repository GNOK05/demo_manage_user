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
import org.springframework.web.bind.annotation.RequestParam;
import com.example.demo.entity.Role;

@RestController
@RequestMapping("/api/v1/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {
    private final LeaveRequestService service;

    @GetMapping("/my")
    public ApiResponse<List<LeaveRequestDto.Response>> my() {
        return ApiResponse.ok(service.myRequests());
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<List<LeaveRequestDto.Response>> user(@PathVariable Long userId) {
        return ApiResponse.ok(service.requestsForUser(userId));
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
    public ApiResponse<LeaveRequestDto.Response> approve(
            @PathVariable Long id,
            @Valid @RequestBody LeaveRequestDto.DecisionRequest request) {
        return ApiResponse.ok(service.approve(id, request.status()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<LeaveRequestDto.Response> cancel(@PathVariable Long id) {
        return ApiResponse.ok(service.cancel(id));
    }

    @GetMapping("/balance")
    public ApiResponse<LeaveRequestDto.BalanceResponse> myBalance(@RequestParam int year) {
        return ApiResponse.ok(service.myBalance(year));
    }

    @GetMapping("/balance/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<LeaveRequestDto.BalanceResponse> userBalance(@PathVariable Long userId, @RequestParam int year) {
        return ApiResponse.ok(service.userBalance(userId, year));
    }

    @PutMapping("/balance/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LeaveRequestDto.BalanceResponse> setBalance(
            @PathVariable Long userId, @RequestParam int year, @RequestBody BalanceRequest request) {
        return ApiResponse.ok(service.setBalance(userId, year, request.entitledDays()));
    }

    public record BalanceRequest(int entitledDays) {}
}
