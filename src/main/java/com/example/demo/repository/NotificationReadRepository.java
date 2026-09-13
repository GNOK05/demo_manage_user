package com.example.demo.repository;

import com.example.demo.entity.NotificationRead;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {
    Optional<NotificationRead> findByUserAndNotificationKey(User user, String notificationKey);
    boolean existsByUserAndNotificationKey(User user, String notificationKey);
}
