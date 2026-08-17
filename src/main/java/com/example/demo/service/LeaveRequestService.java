package com.example.demo.service;

import com.example.demo.dto.LeaveRequestDto;
import com.example.demo.entity.LeaveRequestStatus;

import java.util.List;

public interface LeaveRequestService {
    LeaveRequestDto.Response create(LeaveRequestDto.SaveRequest request);
    List<LeaveRequestDto.Response> myRequests();
    List<LeaveRequestDto.Response> pendingForApproval();
    LeaveRequestDto.Response approve(Long id, LeaveRequestStatus status);
}
