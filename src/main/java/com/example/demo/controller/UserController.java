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
import com.example.demo.dto.UserDto;
import com.example.demo.dto.UserResponse;
import com.example.demo.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/v1/users") @RequiredArgsConstructor
public class UserController {private final UserService service;
 @GetMapping @PreAuthorize("hasRole('ADMIN')") public ApiResponse<List<UserResponse>> all(@RequestParam(defaultValue="") String query,@RequestParam(required=false) Long departmentId){return ApiResponse.ok(service.findAll(query,departmentId));}
 @GetMapping("/department") @PreAuthorize("hasRole('MANAGER')") public ApiResponse<List<UserResponse>> department(@RequestParam(defaultValue="") String query){return ApiResponse.ok(service.departmentMembers(query));}
 @GetMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<UserResponse> get(@PathVariable Long id){return ApiResponse.ok(service.get(id));}
 @PostMapping @PreAuthorize("hasRole('ADMIN')") public ApiResponse<UserResponse> create(@Valid @RequestBody UserDto.SaveRequest r){return ApiResponse.ok(service.create(r));}
 @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<UserResponse> update(@PathVariable Long id,@Valid @RequestBody UserDto.UpdateRequest r){return ApiResponse.ok(service.update(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") public void delete(@PathVariable Long id){service.delete(id);}
}
