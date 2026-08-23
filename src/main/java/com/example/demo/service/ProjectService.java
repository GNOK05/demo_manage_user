package com.example.demo.service;
import java.util.List;

import com.example.demo.dto.ProjectDto;
import com.example.demo.entity.ProjectStatus;
public interface ProjectService { List<ProjectDto.Response> findAccessible(Long departmentId, ProjectStatus status); ProjectDto.Response get(Long id); ProjectDto.Response save(ProjectDto.SaveRequest r); ProjectDto.Response update(Long id, ProjectDto.SaveRequest r); void delete(Long id); }
