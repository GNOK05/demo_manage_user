package com.example.demo.repository;

import com.example.demo.entity.WorkRequest;
import com.example.demo.entity.WorkRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkRequestRepository extends JpaRepository<WorkRequest, Long> {
    List<WorkRequest> findByProjectIdOrderByDeadlineAsc(Long projectId);
    List<WorkRequest> findByExecutingDepartmentIdOrderByDeadlineAsc(Long departmentId);
    Optional<WorkRequest> findFirstByProjectIdOrderByIdAsc(Long projectId);
    boolean existsByRequestCode(String requestCode);
    boolean existsByProjectIdAndExecutingDepartmentId(Long projectId, Long departmentId);
    List<WorkRequest> findByDeadlineBetweenAndStatusNotIn(LocalDate from, LocalDate to, List<WorkRequestStatus> statuses);
}
