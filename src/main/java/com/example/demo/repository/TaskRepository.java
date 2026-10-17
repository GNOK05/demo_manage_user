package com.example.demo.repository;
import com.example.demo.entity.Task;
import com.example.demo.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProjectId(Long projectId);
    List<Task> findByAssignedToIdOrderByDeadlineAsc(Long userId);
    @Query("select t from Task t where t.assignedTo.id = :userId or t.tester.id = :userId order by t.deadline")
    List<Task> findAssignedOrTesting(@Param("userId") Long userId);
    long countByProjectId(Long projectId);
    long countByProjectIdAndStatus(Long projectId, TaskStatus status);
}
