package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity @Table(name = "projects")
@Getter @Setter
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_name", nullable = false, length = 160) private String projectName;
    @Column(length = 2000) private String description;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "department_id", nullable = false) private Department department;
    @Column(nullable = false) private LocalDate startDate;
    @Column(nullable = false) private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ProjectStatus status = ProjectStatus.NOT_STARTED;
}
