package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.DashboardDto;
import com.example.demo.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<DashboardDto.Summary> summary() {
        return ApiResponse.ok(dashboardService.summary());
    }
}
