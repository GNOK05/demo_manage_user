package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.PayrollDto;
import com.example.demo.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/api/v1/payroll")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE')")
public class PayrollController {
    private final PayrollService payroll;

    @GetMapping("/monthly")
    public ApiResponse<PayrollDto.MonthlyReport> monthly(@RequestParam int year, @RequestParam int month) {
        return ApiResponse.ok(payroll.monthly(year, month));
    }

    @GetMapping(value = "/monthly.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<byte[]> excel(@RequestParam int year, @RequestParam int month) {
        String filename = "payroll-inputs-%04d-%02d.xlsx".formatted(year, month);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(payroll.exportExcel(year, month));
    }

    @PostMapping("/{year}/{month}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PayrollDto.MonthlyReport> close(@PathVariable int year, @PathVariable int month) {
        return ApiResponse.ok(payroll.close(year, month));
    }

    @PostMapping("/{year}/{month}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PayrollDto.MonthlyReport> reopen(@PathVariable int year, @PathVariable int month,
                                                         @org.springframework.web.bind.annotation.RequestBody PayrollDto.ReopenRequest request) {
        return ApiResponse.ok(payroll.reopen(year, month, request.reason()));
    }
}