package com.agilify.core.notifications.dto;

import com.agilify.core.domain.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String referencedType,
        UUID referencedId,
        Boolean isRead,
        Instant createdAt
) {
}
