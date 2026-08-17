package com.example.demo.dto;
import com.example.demo.entity.Role;
import jakarta.validation.constraints.*;
public final class UserDto {
    private UserDto() {}
    public record SaveRequest(@NotBlank String username, @NotBlank @Size(min = 6) String password,
        @NotBlank String fullName, @Email @NotBlank String email, String phone, @NotNull Role role, Long departmentId, String jobTitle) {}
    public record UpdateRequest(@NotBlank String fullName, @Email @NotBlank String email, String phone, Role role, Long departmentId, String password, String jobTitle) {}
}
