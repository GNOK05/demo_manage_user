package com.example.demo.service;
import com.example.demo.dto.UserDto;
import com.example.demo.dto.UserResponse;
import com.example.demo.entity.User;
import java.util.List;
public interface UserService {
    UserResponse create(UserDto.SaveRequest request);
    UserResponse update(Long id, UserDto.UpdateRequest request);
    List<UserResponse> findAll();
    UserResponse get(Long id);
    void delete(Long id);
    User currentUser();
    UserResponse currentProfile();
    User findEntity(Long id);
    User findEntityByUsername(String username);
    UserResponse toResponse(User user);
    List<UserResponse> departmentMembers();
}
