package com.example.demo.dto;
import jakarta.validation.constraints.NotBlank;
public final class AuthDto {
    private AuthDto() {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String token, UserResponse user) {}
}
