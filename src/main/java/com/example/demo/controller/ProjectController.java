package com.example.demo.controller;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.ProjectDto;
import com.example.demo.entity.ProjectStatus;
import com.example.demo.service.ProjectService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/v1/projects") @RequiredArgsConstructor
public class ProjectController {private final ProjectService service;
 @GetMapping public ApiResponse<List<ProjectDto.Response>> all(@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Long departmentId,@RequestParam(required=false) ProjectStatus status){return ApiResponse.ok(service.findAccessible(keyword,departmentId,status));}
 @GetMapping("/{id}") public ApiResponse<ProjectDto.Response> get(@PathVariable Long id){return ApiResponse.ok(service.get(id));}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')") public ApiResponse<ProjectDto.Response> create(@Valid @RequestBody ProjectDto.SaveRequest r){return ApiResponse.ok(service.save(r));}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')") public ApiResponse<ProjectDto.Response> update(@PathVariable Long id,@Valid @RequestBody ProjectDto.SaveRequest r){return ApiResponse.ok(service.update(id,r));}
 @GetMapping("/{id}/departments") public ApiResponse<List<ProjectDto.DepartmentRef>> departments(@PathVariable Long id){return ApiResponse.ok(service.departments(id));}
 @PostMapping("/{id}/departments/{departmentId}") @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')") public ApiResponse<List<ProjectDto.DepartmentRef>> addDepartment(@PathVariable Long id,@PathVariable Long departmentId){return ApiResponse.ok(service.addDepartment(id,departmentId));}
 @DeleteMapping("/{id}/departments/{departmentId}") @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')") public ApiResponse<List<ProjectDto.DepartmentRef>> removeDepartment(@PathVariable Long id,@PathVariable Long departmentId){return ApiResponse.ok(service.removeDepartment(id,departmentId));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER')") public void delete(@PathVariable Long id){service.delete(id);}
}
