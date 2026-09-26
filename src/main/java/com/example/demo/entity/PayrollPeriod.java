package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_periods", uniqueConstraints = @UniqueConstraint(columnNames = {"period_year", "period_month"}))
@Getter
@Setter
public class PayrollPeriod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_year", nullable = false)
    private int year;

    @Column(name = "period_month", nullable = false)
    private int month;

    @Column(name = "is_closed", nullable = false)
    private boolean closed = true;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id", nullable = false)
    private User closedBy;

    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reopened_by_id")
    private User reopenedBy;

    @Column(name = "reopen_reason", length = 500)
    private String reopenReason;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void onCreate() { if (closed && closedAt == null) closedAt = LocalDateTime.now(); }
}