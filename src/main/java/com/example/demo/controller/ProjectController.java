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
 @GetMapping public ApiResponse<List<ProjectDto.Response>> all(@RequestParam(required=false) Long departmentId,@RequestParam(required=false) ProjectStatus status){return ApiResponse.ok(service.findAccessible(departmentId,status));}
 @GetMapping("/{id}") public ApiResponse<ProjectDto.Response> get(@PathVariable Long id){return ApiResponse.ok(service.get(id));}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") public ApiResponse<ProjectDto.Response> create(@Valid @RequestBody ProjectDto.SaveRequest r){return ApiResponse.ok(service.save(r));}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") public ApiResponse<ProjectDto.Response> update(@PathVariable Long id,@Valid @RequestBody ProjectDto.SaveRequest r){return ApiResponse.ok(service.update(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") public void delete(@PathVariable Long id){service.delete(id);}
}
