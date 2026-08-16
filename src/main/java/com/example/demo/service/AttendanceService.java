package com.example.demo.service;
import com.example.demo.dto.AttendanceDto;
import java.util.List;
public interface AttendanceService { AttendanceDto.Response checkIn(); AttendanceDto.Response checkOut(); List<AttendanceDto.Response> myAttendance(); List<AttendanceDto.Response> departmentAttendance(); List<AttendanceDto.Response> all(); }
