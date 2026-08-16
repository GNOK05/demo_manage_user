package com.example.demo.service.impl;

import com.example.demo.dto.UserDto;
import com.example.demo.dto.UserResponse;
import com.example.demo.entity.Department;
import com.example.demo.entity.User;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository users; private final DepartmentRepository departments; private final PasswordEncoder encoder;
    @Override public UserResponse create(UserDto.SaveRequest r) {
        if (users.existsByUsername(r.username())) throw new BussinessException("Username already exists");
        if (users.existsByEmail(r.email())) throw new BussinessException("Email already exists");
        User u = new User(); u.setUsername(r.username()); u.setPassword(encoder.encode(r.password()));
        u.setFullName(r.fullName()); u.setEmail(r.email()); u.setPhone(r.phone()); u.setRole(r.role()); u.setDepartment(department(r.departmentId()));
        return response(users.save(u));
    }
    @Override public UserResponse update(Long id, UserDto.UpdateRequest r) {
        User u = findEntity(id); u.setFullName(r.fullName()); u.setEmail(r.email()); u.setPhone(r.phone());
        if (r.role() != null) u.setRole(r.role()); if (r.departmentId() != null) u.setDepartment(department(r.departmentId()));
        if (r.password() != null && !r.password().isBlank()) u.setPassword(encoder.encode(r.password())); return response(u);
    }
    @Override @Transactional(readOnly = true) public List<UserResponse> findAll() { return users.findAll().stream().map(this::response).toList(); }
    @Override @Transactional(readOnly = true) public UserResponse get(Long id) { return response(findEntity(id)); }
    @Override public void delete(Long id) { users.delete(findEntity(id)); }
    @Override @Transactional(readOnly = true) public User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return users.findByUsername(username).orElseThrow(() -> new BussinessException("Current user not found"));
    }
    @Override public UserResponse currentProfile() { return response(currentUser()); }
    @Override @Transactional(readOnly = true) public User findEntity(Long id) { return users.findById(id).orElseThrow(() -> new BussinessException("User not found: " + id)); }
    @Override @Transactional(readOnly = true) public User findEntityByUsername(String username) { return users.findByUsername(username).orElseThrow(() -> new BussinessException("User not found")); }
    @Override public UserResponse toResponse(User user) { return response(user); }
    @Override @Transactional(readOnly = true) public List<UserResponse> departmentMembers() { User current=currentUser(); if (current.getDepartment()==null) throw new BussinessException("You do not belong to a department"); return users.findByDepartmentId(current.getDepartment().getId()).stream().map(this::response).toList(); }
    private Department department(Long id) { return id == null ? null : departments.findById(id).orElseThrow(() -> new BussinessException("Department not found: " + id)); }
    public UserResponse response(User u) { return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), u.getDepartment() == null ? null : u.getDepartment().getId(), u.getDepartment() == null ? null : u.getDepartment().getName(), u.getCreatedAt()); }
}
