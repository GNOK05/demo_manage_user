package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.HealthCheckService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    private final HealthCheckService healthCheckService;

    public HealthController(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = Map.of(
                "status", "ok",
                "timestamp", Instant.now().toString()
        );
        return ApiResponse.ok(body);
    }

    @GetMapping("/health/readiness")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readiness() {
        boolean databaseUp = healthCheckService.isDatabaseUp();
        Map<String, Object> body = Map.of(
                "status", databaseUp ? "ok" : "down",
                "database", databaseUp ? "up" : "down",
                "timestamp", Instant.now().toString()
        );
        HttpStatus status = databaseUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        ApiResponse<Map<String, Object>> response = new ApiResponse<>(
            body,
            databaseUp ? "Success" : "Service unavailable"
        );
        return ResponseEntity.status(status).body(response);
    }
}
