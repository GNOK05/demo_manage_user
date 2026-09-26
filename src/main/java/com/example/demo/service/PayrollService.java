package com.example.demo.service;

import com.example.demo.dto.PayrollDto;

import java.time.YearMonth;

public interface PayrollService {
    PayrollDto.MonthlyReport monthly(int year, int month);
    byte[] exportExcel(int year, int month);
    PayrollDto.MonthlyReport close(int year, int month);
    PayrollDto.MonthlyReport reopen(int year, int month, String reason);
    boolean isClosed(YearMonth period);
}