package com.example.demo.service;
import com.example.demo.dto.request.CreateUserDTO;
import com.example.demo.dto.request.UpdateUserDTO;
import com.example.demo.entity.Group;
import com.example.demo.entity.User;
import com.example.demo.entity.UserFiller;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.time.LocalDate;
import java.util.List;

public interface  UserService extends UserDetailsService {
    User create(CreateUserDTO request);
    User updateUser( Integer id, UpdateUserDTO update);
    List<User> search(UserFiller userFiller);
    void deleteByID(Integer id);
    List<User> findByBirthdayBetween(LocalDate fromDate, LocalDate toDate);
    Page<User> getAllUsers(Pageable pageable);
    List<Group> findGroupByUserId( Integer userId);

    }
