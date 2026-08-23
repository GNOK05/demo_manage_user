package com.example.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.entity.User;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByDepartmentId(Long departmentId);
    @Query("select u from User u left join u.department d where (:departmentId is null or d.id = :departmentId) and (:query = '' or lower(u.username) like lower(concat('%', :query, '%')) or lower(u.fullName) like lower(concat('%', :query, '%')) or lower(u.email) like lower(concat('%', :query, '%')) or lower(coalesce(u.jobTitle, '')) like lower(concat('%', :query, '%'))) order by u.fullName")
    List<User> search(@Param("query") String query, @Param("departmentId") Long departmentId);
}
