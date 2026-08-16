package com.example.demo.repository;
import com.example.demo.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByDepartmentId(Long departmentId);
    @Query("select distinct t.project from Task t where t.assignedTo.id = :userId")
    List<Project> findAssignedToUser(@Param("userId") Long userId);
}
