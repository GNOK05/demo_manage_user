package com.example.demo.repository;

import com.example.demo.entity.WorkRequestHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkRequestHistoryRepository extends JpaRepository<WorkRequestHistory, Long> {
    List<WorkRequestHistory> findByWorkRequestIdOrderByChangedAtDesc(Long workRequestId);
}
