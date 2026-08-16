package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "attendance", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "attendance_date"}))
@Getter @Setter
public class Attendance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "attendance_date", nullable = false) private LocalDate date;
    @Column(name = "check_in_time") private LocalDateTime checkInTime;
    @Column(name = "check_out_time") private LocalDateTime checkOutTime;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AttendanceStatus status;
}
