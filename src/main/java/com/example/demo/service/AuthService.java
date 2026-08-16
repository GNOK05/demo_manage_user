package com.example.demo.service;
import com.example.demo.dto.AuthDto;
public interface AuthService { AuthDto.LoginResponse login(AuthDto.LoginRequest request); }
