package com.example.demo.service;
import java.util.List;

import com.example.demo.dto.UserDto;
import com.example.demo.dto.UserResponse;
import com.example.demo.entity.User;
public interface UserService {
    UserResponse create(UserDto.SaveRequest request);
    UserResponse update(Long id, UserDto.UpdateRequest request);
    List<UserResponse> findAll(String query, Long departmentId);
    UserResponse get(Long id);
    void delete(Long id);
    User currentUser();
    UserResponse currentProfile();
    User findEntity(Long id);
    User findEntityByUsername(String username);
    UserResponse toResponse(User user);
    List<UserResponse> departmentMembers(String query);
}
