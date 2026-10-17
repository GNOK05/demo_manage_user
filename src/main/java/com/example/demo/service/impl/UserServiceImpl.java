package com.example.demo.service.impl;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.UserDto;
import com.example.demo.dto.UserResponse;
import com.example.demo.entity.Department;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;

import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor @Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository users; private final DepartmentRepository departments; private final PasswordEncoder encoder;
    @Override public UserResponse create(UserDto.SaveRequest r) {
        User current = currentUser();
        validateRoleAssignment(current, r.role());
        if (users.existsByUsername(r.username())) throw new BussinessException("Username already exists");
        if (users.existsByEmail(r.email())) throw new BussinessException("Email already exists");
        User u = new User(); u.setUsername(r.username()); u.setPassword(encoder.encode(r.password()));
        u.setFullName(r.fullName()); u.setEmail(r.email()); u.setPhone(r.phone()); u.setRole(r.role()); u.setDepartment(department(r.departmentId()));
        u.setJobTitle(r.jobTitle());
        return response(users.save(u));
    }
    @Override public UserResponse update(Long id, UserDto.UpdateRequest r) {
        User current = currentUser();
        User u = findEntity(id);
        if (current.getRole() == Role.HR && (u.getRole() == Role.ADMIN || u.getRole() == Role.HR)) {
            throw new AccessDeniedException("HR cannot edit ADMIN or HR accounts.");
        }
        if (r.role() != null && r.role() != u.getRole()) validateRoleAssignment(current, r.role());
        users.findByEmail(r.email()).filter(existing -> !existing.getId().equals(id)).ifPresent(existing -> {
            throw new BussinessException("Email already exists");
        });
        u.setFullName(r.fullName()); u.setEmail(r.email()); u.setPhone(r.phone());
        if (r.role() != null) u.setRole(r.role());
        if (r.departmentId() != null) u.setDepartment(department(r.departmentId()));
        if (r.jobTitle() != null) u.setJobTitle(r.jobTitle());
        if (r.password() != null && !r.password().isBlank()) u.setPassword(encoder.encode(r.password())); return response(u);
    }
    @Override @Transactional(readOnly = true) public List<UserResponse> findAll(String query, Long departmentId) { return users.search(query == null ? "" : query.trim(), departmentId).stream().map(this::response).toList(); }
    @Override @Transactional(readOnly = true) public UserResponse get(Long id) { return response(findEntity(id)); }
    @Override public void delete(Long id) {
        User current = currentUser();
        User target = findEntity(id);
        if (current.getId().equals(target.getId()) || roleRank(current.getRole()) <= roleRank(target.getRole())) {
            throw new AccessDeniedException("You do not have permission to delete this user.");
        }
        if (current.getRole() != Role.ADMIN && current.getRole() != Role.HR
                && (current.getDepartment() == null || target.getDepartment() == null
                || !current.getDepartment().getId().equals(target.getDepartment().getId()))) {
            throw new AccessDeniedException("You can only delete users in your department.");
        }
        users.delete(target);
    }

    private int roleRank(Role role) {
        return switch (role) {
            case ADMIN -> 7;
            case HR -> 6;
            case PROJECT_MANAGER -> 5;
            case TEAM_LEAD -> 4;
            case QA -> 3;
            case MEMBER -> 2;
            case VIEWER -> 1;
        };
    }

    private void validateRoleAssignment(User current, Role requestedRole) {
        if (!current.getRole().canManageUsers()) {
            throw new AccessDeniedException("You do not have permission to manage users.");
        }
        if (current.getRole() == Role.HR && (requestedRole == Role.ADMIN || requestedRole == Role.HR)) {
            throw new AccessDeniedException("HR cannot assign ADMIN or HR roles.");
        }
    }
    @Override @Transactional(readOnly = true) public User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return users.findByUsername(username).orElseThrow(() -> new BussinessException("Current user not found"));
    }
    @Override public UserResponse currentProfile() { return response(currentUser()); }
    @Override public void changeCurrentPassword(String currentPassword, String newPassword) {
        User user = currentUser();
        if (!encoder.matches(currentPassword, user.getPassword())) {
            throw new BussinessException("Current password is incorrect");
        }
        if (encoder.matches(newPassword, user.getPassword())) {
            throw new BussinessException("New password must be different from the current password");
        }
        user.setPassword(encoder.encode(newPassword));
        users.save(user);
    }
    @Override @Transactional(readOnly = true) public User findEntity(Long id) { return users.findById(id).orElseThrow(() -> new BussinessException("User not found: " + id)); }
    @Override @Transactional(readOnly = true) public User findEntityByUsername(String username) { return users.findByUsername(username).orElseThrow(() -> new BussinessException("User not found")); }
    @Override public UserResponse toResponse(User user) { return response(user); }
    @Override @Transactional(readOnly = true) public List<UserResponse> departmentMembers(String query) { User current=currentUser(); if (current.getDepartment()==null) throw new BussinessException("You do not belong to a department"); return users.search(query == null ? "" : query.trim(), current.getDepartment().getId()).stream().map(this::response).toList(); }
    private Department department(Long id) { return id == null ? null : departments.findById(id).orElseThrow(() -> new BussinessException("Department not found: " + id)); }
    public UserResponse response(User u) { return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), u.getDepartment() == null ? null : u.getDepartment().getId(), u.getDepartment() == null ? null : u.getDepartment().getName(), u.getJobTitle(), u.getCreatedAt()); }
}
