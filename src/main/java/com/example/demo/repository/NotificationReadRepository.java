package com.example.demo.repository;

import com.example.demo.entity.NotificationRead;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {
    boolean existsByUserAndNotificationKey(User user, String notificationKey);
}
