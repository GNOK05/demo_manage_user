package com.example.demo.repository;

import com.example.demo.entity.LeaveRequest;
import com.example.demo.entity.LeaveRequestStatus;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByUserOrderByCreatedAtDesc(User user);
    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveRequestStatus status);
    List<LeaveRequest> findByStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(LeaveRequestStatus status, java.time.LocalDate date1, java.time.LocalDate date2);
    List<LeaveRequest> findByUserDepartmentIdOrderByCreatedAtDesc(Long departmentId);
}
