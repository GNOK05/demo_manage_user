package com.example.demo.controller;
import com.example.demo.dto.*;
import com.example.demo.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/tasks") @RequiredArgsConstructor
public class TaskController {private final TaskService service;
 @GetMapping("/project/{projectId}") public ApiResponse<List<TaskDto.Response>> project(@PathVariable Long projectId){return ApiResponse.ok(service.byProject(projectId));}
 @GetMapping("/my") public ApiResponse<List<TaskDto.Response>> mine(){return ApiResponse.ok(service.myTasks());}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") public ApiResponse<TaskDto.Response> create(@Valid @RequestBody TaskDto.SaveRequest r){return ApiResponse.ok(service.save(r));}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") public ApiResponse<TaskDto.Response> update(@PathVariable Long id,@Valid @RequestBody TaskDto.SaveRequest r){return ApiResponse.ok(service.update(id,r));}
 @PatchMapping("/{id}/status") public ApiResponse<TaskDto.Response> status(@PathVariable Long id,@Valid @RequestBody TaskDto.StatusRequest r){return ApiResponse.ok(service.updateStatus(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") public void delete(@PathVariable Long id){service.delete(id);}
}
