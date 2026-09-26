package com.example.demo.repository;

import com.example.demo.entity.PayrollPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {
    Optional<PayrollPeriod> findByYearAndMonth(int year, int month);
}