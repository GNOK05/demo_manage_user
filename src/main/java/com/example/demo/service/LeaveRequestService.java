package com.example.demo.service;

import com.example.demo.dto.LeaveRequestDto;
import com.example.demo.entity.LeaveRequestStatus;

import java.util.List;

public interface LeaveRequestService {
    LeaveRequestDto.Response create(LeaveRequestDto.SaveRequest request);
    List<LeaveRequestDto.Response> myRequests();
    List<LeaveRequestDto.Response> requestsForUser(Long userId);
    List<LeaveRequestDto.Response> pendingForApproval();
    LeaveRequestDto.Response approve(Long id, LeaveRequestStatus status);
    LeaveRequestDto.Response cancel(Long id);
    LeaveRequestDto.BalanceResponse myBalance(int year);
    LeaveRequestDto.BalanceResponse userBalance(Long userId, int year);
    LeaveRequestDto.BalanceResponse setBalance(Long userId, int year, int entitledDays);
}
