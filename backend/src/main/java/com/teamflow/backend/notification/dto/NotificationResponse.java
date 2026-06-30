package com.teamflow.backend.notification.dto;

import com.teamflow.backend.notification.Notification;
import com.teamflow.backend.notification.NotificationType;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String message,
        Long workspaceId,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getWorkspaceId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
