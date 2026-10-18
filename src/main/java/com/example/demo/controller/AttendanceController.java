package com.example.demo.controller;
import com.example.demo.dto.*;
import com.example.demo.service.AttendanceService;
import com.example.demo.service.AttendanceAdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
@RestController @RequestMapping("/api/v1/attendance") @RequiredArgsConstructor
public class AttendanceController {private final AttendanceService service; private final AttendanceAdjustmentService adjustments;
 @PostMapping("/check-in") public ApiResponse<AttendanceDto.Response> in(){return ApiResponse.ok(service.checkIn());}
 @PostMapping("/check-out") public ApiResponse<AttendanceDto.Response> out(){return ApiResponse.ok(service.checkOut());}
 @GetMapping("/my") public ApiResponse<List<AttendanceDto.Response>> mine(){return ApiResponse.ok(service.myAttendance());}
 @GetMapping("/department") @PreAuthorize("hasAnyRole('PROJECT_MANAGER','TEAM_LEAD')") public ApiResponse<List<AttendanceDto.Response>> department(){return ApiResponse.ok(service.departmentAttendance());}
 @GetMapping("/department/summary") @PreAuthorize("hasAnyRole('PROJECT_MANAGER','TEAM_LEAD')") public ApiResponse<AttendanceDto.DepartmentSummary> departmentSummary(@RequestParam java.time.LocalDate date){return ApiResponse.ok(service.departmentSummary(date));}
 @GetMapping("/day") public ApiResponse<List<AttendanceDto.Response>> day(@RequestParam java.time.LocalDate date){return ApiResponse.ok(service.byDate(date));}
 @GetMapping("/user/{userId}") @PreAuthorize("hasAnyRole('ADMIN','HR','PROJECT_MANAGER','TEAM_LEAD')") public ApiResponse<List<AttendanceDto.Response>> employee(@PathVariable Long userId, @RequestParam @Min(1) @Max(12) int month, @RequestParam @Min(2000) int year){return ApiResponse.ok(service.employeeAttendance(userId, month, year));}
 @GetMapping @PreAuthorize("hasAnyRole('ADMIN','HR')") public ApiResponse<List<AttendanceDto.Response>> all(){return ApiResponse.ok(service.all());}
 @PostMapping("/adjustments") public ApiResponse<AttendanceAdjustmentDto.Response> requestAdjustment(@jakarta.validation.Valid @RequestBody AttendanceAdjustmentDto.SaveRequest request){return ApiResponse.ok(adjustments.create(request));}
 @GetMapping("/adjustments/my") public ApiResponse<List<AttendanceAdjustmentDto.Response>> myAdjustments(){return ApiResponse.ok(adjustments.mine());}
 @GetMapping("/adjustments/pending") @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')") public ApiResponse<List<AttendanceAdjustmentDto.Response>> pendingAdjustments(){return ApiResponse.ok(adjustments.pending());}
 @PatchMapping("/adjustments/{id}/decision") @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')") public ApiResponse<AttendanceAdjustmentDto.Response> decideAdjustment(@PathVariable Long id,@jakarta.validation.Valid @RequestBody AttendanceAdjustmentDto.DecisionRequest request){return ApiResponse.ok(adjustments.decide(id,request.status()));}
}
