package com.example.demo.repository;

import com.example.demo.entity.AttendanceAdjustmentRequest;
import com.example.demo.entity.AttendanceAdjustmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceAdjustmentRequestRepository extends JpaRepository<AttendanceAdjustmentRequest, Long> {
    List<AttendanceAdjustmentRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AttendanceAdjustmentRequest> findByUserIdOrRequestedByIdOrderByCreatedAtDesc(Long userId, Long requestedById);

    List<AttendanceAdjustmentRequest> findByStatusOrderByCreatedAtAsc(AttendanceAdjustmentStatus status);

    boolean existsBySession_IdAndStatusIn(Long sessionId, List<AttendanceAdjustmentStatus> statuses);
}