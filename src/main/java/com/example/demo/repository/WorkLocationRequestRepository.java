package com.example.demo.repository;

import com.example.demo.entity.LeaveRequestStatus;
import com.example.demo.entity.User;
import com.example.demo.entity.WorkLocationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkLocationRequestRepository extends JpaRepository<WorkLocationRequest, Long> {
    List<WorkLocationRequest> findByUserOrderByCreatedAtDesc(User user);
    List<WorkLocationRequest> findByStatusOrderByCreatedAtDesc(LeaveRequestStatus status);
    List<WorkLocationRequest> findByUserDepartmentIdOrderByCreatedAtDesc(Long departmentId);
}
