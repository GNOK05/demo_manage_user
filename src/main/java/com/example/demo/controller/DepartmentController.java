package com.example.demo.controller;
import com.example.demo.dto.*;
import com.example.demo.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/departments") @RequiredArgsConstructor
public class DepartmentController {private final DepartmentService service;
 @GetMapping public ApiResponse<List<DepartmentDto.Response>> all(){return ApiResponse.ok(service.findAll());}
 @GetMapping("/{id}") public ApiResponse<DepartmentDto.Response> get(@PathVariable Long id){return ApiResponse.ok(service.get(id));}
 @PostMapping @PreAuthorize("hasRole('ADMIN')") public ApiResponse<DepartmentDto.Response> create(@Valid @RequestBody DepartmentDto.SaveRequest r){return ApiResponse.ok(service.save(r));}
 @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<DepartmentDto.Response> update(@PathVariable Long id,@Valid @RequestBody DepartmentDto.SaveRequest r){return ApiResponse.ok(service.update(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public void delete(@PathVariable Long id){service.delete(id);}
}
