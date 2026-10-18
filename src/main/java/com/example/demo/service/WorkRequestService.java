package com.example.demo.service;

import com.example.demo.dto.WorkRequestDto;
import com.example.demo.entity.TaskPriority;
import com.example.demo.entity.WorkRequestHealth;
import com.example.demo.entity.WorkRequestStatus;

import java.util.List;

public interface WorkRequestService {
    List<WorkRequestDto.Response> search(String keyword, Long projectId, Long departmentId,
        WorkRequestStatus status, TaskPriority priority, WorkRequestHealth health, boolean overdue);
    List<WorkRequestDto.Response> byProject(Long projectId);
    WorkRequestDto.Response get(Long id);
    WorkRequestDto.Response create(Long projectId, WorkRequestDto.SaveRequest request);
    WorkRequestDto.Response update(Long id, WorkRequestDto.SaveRequest request);
    WorkRequestDto.Response updateStatus(Long id, WorkRequestDto.StatusRequest request);
    void delete(Long id);
}
