package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity @Table(name = "tasks")
@Getter @Setter
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "task_name", nullable = false, length = 160) private String taskName;
    @Column(length = 2000) private String description;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_to_id") private User assignedTo;
    /** QA/QC/Tester assigned to verify this task, independent of the implementer in {@link #assignedTo}. */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tester_id") private User tester;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_id", nullable = false) private User createdBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TaskStatus status = TaskStatus.TODO;
    @Column(nullable = false) private LocalDate deadline;
}
