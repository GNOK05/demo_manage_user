package com.example.demo.service;
import java.util.List;

import com.example.demo.dto.ProjectDto;
import com.example.demo.entity.ProjectStatus;
public interface ProjectService {
    List<ProjectDto.Response> findAccessible(String keyword, Long departmentId, ProjectStatus status);
    ProjectDto.Response get(Long id);
    ProjectDto.Response save(ProjectDto.SaveRequest r);
    ProjectDto.Response update(Long id, ProjectDto.SaveRequest r);
    List<ProjectDto.DepartmentRef> departments(Long id);
    List<ProjectDto.DepartmentRef> addDepartment(Long id, Long departmentId);
    List<ProjectDto.DepartmentRef> removeDepartment(Long id, Long departmentId);
    void delete(Long id);
}
