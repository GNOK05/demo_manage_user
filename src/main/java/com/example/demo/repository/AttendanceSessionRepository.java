package com.example.demo.repository;

import com.example.demo.entity.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    List<AttendanceSession> findByAttendanceIdOrderByCheckInTimeAsc(Long attendanceId);

    Optional<AttendanceSession> findFirstByAttendanceIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(Long attendanceId);
}