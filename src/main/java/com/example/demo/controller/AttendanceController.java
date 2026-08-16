package com.example.demo.controller;
import com.example.demo.dto.*;
import com.example.demo.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/attendance") @RequiredArgsConstructor
public class AttendanceController {private final AttendanceService service;
 @PostMapping("/check-in") public ApiResponse<AttendanceDto.Response> in(){return ApiResponse.ok(service.checkIn());}
 @PostMapping("/check-out") public ApiResponse<AttendanceDto.Response> out(){return ApiResponse.ok(service.checkOut());}
 @GetMapping("/my") public ApiResponse<List<AttendanceDto.Response>> mine(){return ApiResponse.ok(service.myAttendance());}
 @GetMapping("/department") @PreAuthorize("hasRole('MANAGER')") public ApiResponse<List<AttendanceDto.Response>> department(){return ApiResponse.ok(service.departmentAttendance());}
 @GetMapping @PreAuthorize("hasRole('ADMIN')") public ApiResponse<List<AttendanceDto.Response>> all(){return ApiResponse.ok(service.all());}
}
