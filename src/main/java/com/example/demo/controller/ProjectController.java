package com.example.demo.controller;
import com.example.demo.dto.*;
import com.example.demo.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/projects") @RequiredArgsConstructor
public class ProjectController {private final ProjectService service;
 @GetMapping public ApiResponse<List<ProjectDto.Response>> all(){return ApiResponse.ok(service.findAccessible());}
 @GetMapping("/{id}") public ApiResponse<ProjectDto.Response> get(@PathVariable Long id){return ApiResponse.ok(service.get(id));}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") public ApiResponse<ProjectDto.Response> create(@Valid @RequestBody ProjectDto.SaveRequest r){return ApiResponse.ok(service.save(r));}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") public ApiResponse<ProjectDto.Response> update(@PathVariable Long id,@Valid @RequestBody ProjectDto.SaveRequest r){return ApiResponse.ok(service.update(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") public void delete(@PathVariable Long id){service.delete(id);}
}
