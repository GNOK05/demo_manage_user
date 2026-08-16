package com.example.demo.service;
import com.example.demo.dto.TaskDto;
import java.util.List;
public interface TaskService { List<TaskDto.Response> byProject(Long projectId); List<TaskDto.Response> myTasks(); TaskDto.Response save(TaskDto.SaveRequest r); TaskDto.Response update(Long id, TaskDto.SaveRequest r); TaskDto.Response updateStatus(Long id, TaskDto.StatusRequest r); void delete(Long id); }
