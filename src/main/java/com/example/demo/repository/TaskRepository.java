package com.example.demo.repository;
import com.example.demo.entity.Task;
import com.example.demo.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProjectId(Long projectId);
    List<Task> findByAssignedToIdOrderByDeadlineAsc(Long userId);
    long countByProjectId(Long projectId);
    long countByProjectIdAndStatus(Long projectId, TaskStatus status);
}
