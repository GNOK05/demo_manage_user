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
                Long relatedId,
                String key,
                boolean unread
        ) {
            public Response(Long id, String type, String title, String message, String priority,
                            LocalDateTime createdAt, Long relatedId) {
                this(id, type, title, message, priority, createdAt, relatedId, type + ":" + relatedId, true);
            }
        }

            public record ReadRequest(String key) {}
}
