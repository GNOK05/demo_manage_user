package com.example.demo.repository;

import com.example.demo.entity.ProjectDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectDepartmentRepository extends JpaRepository<ProjectDepartment, Long> {
    List<ProjectDepartment> findByProjectIdOrderByParticipationRoleAscDepartmentNameAsc(Long projectId);
    List<ProjectDepartment> findByDepartmentId(Long departmentId);
    boolean existsByProjectIdAndDepartmentId(Long projectId, Long departmentId);
    void deleteByProjectIdAndDepartmentId(Long projectId, Long departmentId);
}
