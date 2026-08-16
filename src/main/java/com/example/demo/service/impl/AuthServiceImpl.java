package com.example.demo.service.impl;
import com.example.demo.dto.AuthDto;
import com.example.demo.dto.UserResponse;
import com.example.demo.entity.User;
import com.example.demo.security.JwtService;
import com.example.demo.service.AuthService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
 private final AuthenticationManager authenticationManager; private final JwtService jwt; private final UserService users;
 public AuthDto.LoginResponse login(AuthDto.LoginRequest r){authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(r.username(),r.password()));User u=users.findEntityByUsername(r.username());return new AuthDto.LoginResponse(jwt.generate(u),users.toResponse(u));}
}
