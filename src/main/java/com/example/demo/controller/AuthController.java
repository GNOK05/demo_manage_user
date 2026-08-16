package com.example.demo.controller;
import com.example.demo.dto.*;
import com.example.demo.service.AuthService;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/auth") @RequiredArgsConstructor
public class AuthController { private final AuthService auth; private final UserService users;
 @PostMapping("/login") public ApiResponse<AuthDto.LoginResponse> login(@Valid @RequestBody AuthDto.LoginRequest r){return ApiResponse.ok(auth.login(r));}
 @GetMapping("/me") public ApiResponse<UserResponse> me(){return ApiResponse.ok(users.currentProfile());}
}
