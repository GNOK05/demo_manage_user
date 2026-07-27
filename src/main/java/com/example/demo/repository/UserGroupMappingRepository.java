package com.example.demo.repository;
import com.example.demo.entity.UserGroupMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;


public interface UserGroupMappingRepository extends JpaRepository<UserGroupMapping,Integer>, JpaSpecificationExecutor<UserGroupMapping>{
    boolean existsByGroupIdAndUserId(Integer groupId ,Integer userId);
    Optional<UserGroupMapping> findByGroupIdAndUserId(Integer groupId, Integer userId);
    void deleteByUserId(Integer userID);
}
