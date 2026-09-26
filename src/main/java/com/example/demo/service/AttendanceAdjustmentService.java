package com.example.demo.service;

import com.example.demo.dto.AttendanceAdjustmentDto;
import com.example.demo.entity.AttendanceAdjustmentStatus;
import java.util.List;

public interface AttendanceAdjustmentService {
    AttendanceAdjustmentDto.Response create(AttendanceAdjustmentDto.SaveRequest request);
    List<AttendanceAdjustmentDto.Response> mine();
    List<AttendanceAdjustmentDto.Response> pending();
    AttendanceAdjustmentDto.Response decide(Long id, AttendanceAdjustmentStatus status);
}