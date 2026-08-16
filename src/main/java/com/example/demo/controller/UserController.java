package com.example.demo.controller;
import com.example.demo.dto.*;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/users") @RequiredArgsConstructor
public class UserController {private final UserService service;
 @GetMapping @PreAuthorize("hasRole('ADMIN')") public ApiResponse<List<UserResponse>> all(){return ApiResponse.ok(service.findAll());}
 @GetMapping("/department") @PreAuthorize("hasRole('MANAGER')") public ApiResponse<List<UserResponse>> department(){return ApiResponse.ok(service.departmentMembers());}
 @GetMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<UserResponse> get(@PathVariable Long id){return ApiResponse.ok(service.get(id));}
 @PostMapping @PreAuthorize("hasRole('ADMIN')") public ApiResponse<UserResponse> create(@Valid @RequestBody UserDto.SaveRequest r){return ApiResponse.ok(service.create(r));}
 @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<UserResponse> update(@PathVariable Long id,@Valid @RequestBody UserDto.UpdateRequest r){return ApiResponse.ok(service.update(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public void delete(@PathVariable Long id){service.delete(id);}
}
