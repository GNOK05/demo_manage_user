package com.example.demo.dto;
import com.example.demo.entity.Role;
import java.time.LocalDateTime;
public record UserResponse(Long id, String username, String fullName, String email, String phone, Role role,
                           Long departmentId, String departmentName, LocalDateTime createdAt) {}
