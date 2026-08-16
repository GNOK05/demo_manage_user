package com.example.demo.service;
import com.example.demo.dto.ProjectDto;
import java.util.List;
public interface ProjectService { List<ProjectDto.Response> findAccessible(); ProjectDto.Response get(Long id); ProjectDto.Response save(ProjectDto.SaveRequest r); ProjectDto.Response update(Long id, ProjectDto.SaveRequest r); void delete(Long id); }
