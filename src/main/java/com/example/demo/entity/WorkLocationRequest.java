package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_location_requests")
@Getter
@Setter
public class WorkLocationRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private WorkLocationType type;
    @Column(name = "from_date", nullable = false) private LocalDate fromDate;
    @Column(name = "to_date", nullable = false) private LocalDate toDate;
    @Enumerated(EnumType.STRING) @Column(name = "from_day_part", nullable = false, length = 20) private LeaveDayPart fromDayPart = LeaveDayPart.MORNING;
    @Enumerated(EnumType.STRING) @Column(name = "to_day_part", nullable = false, length = 20) private LeaveDayPart toDayPart = LeaveDayPart.AFTERNOON;
    @Column(name = "client_name", length = 150) private String clientName;
    @Column(name = "client_address", length = 300) private String clientAddress;
    @Column(nullable = false, length = 500) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private LeaveRequestStatus status = LeaveRequestStatus.PENDING_MANAGER;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "manager_approved_by_id") private User managerApprovedBy;
    @Column(name = "manager_approved_at") private LocalDateTime managerApprovedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "admin_approved_by_id") private User adminApprovedBy;
    @Column(name = "admin_approved_at") private LocalDateTime adminApprovedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cancelled_by_id") private User cancelledBy;
    @Column(name = "cancelled_at") private LocalDateTime cancelledAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Version @Column(nullable = false) private Long version;

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
