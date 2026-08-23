package com.example.demo.dto;

import java.time.LocalDateTime;

public final class NotificationDto {
    private NotificationDto() {}

    public record Response(
            Long id,
            String type,
            String title,
            String message,
            String priority,
            LocalDateTime createdAt,
            Long relatedId
    ) {}
}
