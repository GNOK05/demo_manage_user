package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "work_request_history") @Getter @Setter
public class WorkRequestHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "work_request_id") private WorkRequest workRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id") private User actor;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 30) private WorkRequestStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 30) private WorkRequestStatus toStatus;
    @Column(length = 500) private String reason;
    @Column(name = "changed_at", nullable = false) private LocalDateTime changedAt = LocalDateTime.now();
}
