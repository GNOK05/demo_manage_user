package com.example.demo.controller;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.TaskDto;
import com.example.demo.service.TaskService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/v1/tasks") @RequiredArgsConstructor
public class TaskController {private final TaskService service;
 @GetMapping public ApiResponse<List<TaskDto.Response>> accessible(){return ApiResponse.ok(service.accessibleTasks());}
 @GetMapping("/project/{projectId}") public ApiResponse<List<TaskDto.Response>> project(@PathVariable Long projectId){return ApiResponse.ok(service.byProject(projectId));}
 @GetMapping("/my") public ApiResponse<List<TaskDto.Response>> mine(){return ApiResponse.ok(service.myTasks());}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") public ApiResponse<TaskDto.Response> create(@Valid @RequestBody TaskDto.SaveRequest r){return ApiResponse.ok(service.save(r));}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") public ApiResponse<TaskDto.Response> update(@PathVariable Long id,@Valid @RequestBody TaskDto.SaveRequest r){return ApiResponse.ok(service.update(id,r));}
 @PatchMapping("/{id}/status") public ApiResponse<TaskDto.Response> status(@PathVariable Long id,@Valid @RequestBody TaskDto.StatusRequest r){return ApiResponse.ok(service.updateStatus(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public void delete(@PathVariable Long id){service.delete(id);}
}
