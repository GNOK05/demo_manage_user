package com.example.demo.repository;

import com.example.demo.entity.AnnualLeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnnualLeaveBalanceRepository extends JpaRepository<AnnualLeaveBalance, Long> {
    Optional<AnnualLeaveBalance> findByUserIdAndYear(Long userId, int year);
}