package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = Map.of(
                "status", "ok",
                "timestamp", Instant.now().toString()
        );
        return ApiResponse.ok(body);
    }
}
