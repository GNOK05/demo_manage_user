package com.example.demo.service;
import com.example.demo.dto.TaskDto;
import com.example.demo.entity.Role;
import com.example.demo.entity.TaskStatus;
import java.util.List;
public interface TaskService { List<TaskDto.Response> byProject(Long projectId); List<TaskDto.Response> myTasks(); List<TaskDto.Response> accessibleTasks(String keyword, Role assignedRole, TaskStatus status, String sort); TaskDto.Response save(TaskDto.SaveRequest r); TaskDto.Response update(Long id, TaskDto.SaveRequest r); TaskDto.Response updateStatus(Long id, TaskDto.StatusRequest r); void delete(Long id); }
