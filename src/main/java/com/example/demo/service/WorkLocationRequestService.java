package com.example.demo.service;

import com.example.demo.dto.WorkLocationRequestDto;
import com.example.demo.entity.LeaveRequestStatus;

import java.util.List;

public interface WorkLocationRequestService {
    WorkLocationRequestDto.Response create(WorkLocationRequestDto.SaveRequest request);
    List<WorkLocationRequestDto.Response> myRequests();
    List<WorkLocationRequestDto.Response> pendingForApproval();
    WorkLocationRequestDto.Response decide(Long id, LeaveRequestStatus status);
    WorkLocationRequestDto.Response cancel(Long id);
}
