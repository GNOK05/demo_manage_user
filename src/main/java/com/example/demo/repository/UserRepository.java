package com.example.demo.repository;

import com.example.demo.entity.Group;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;


public interface UserRepository extends JpaRepository< User,Integer>, JpaSpecificationExecutor<User> {
    @Query (value ="SELECT * FROM users WHERE birthday BETWEEN :fromDate And :toDate",nativeQuery = true)
    List<User> findByBirthdayBetweenSQL(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    User findByUsername(String username);
    @Query(value = "SELECT g.* from `groups`g where g.id in (SELECT ugm.group_id from user_group_mapping ugm  join users u on ugm.user_id=u.id where ugm.user_id= :userId )", nativeQuery = true)
    List<Group> findGroupdByUserId(Integer userId);
}
