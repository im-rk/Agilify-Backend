package com.agilify.core.notifications.service;

import com.agilify.core.notifications.repository.NotificationRepository;
import com.agilify.core.auth.repository.UserRepository;
import com.agilify.core.common.exception.NotFoundException;
import com.agilify.core.domain.entities.Notification;
import com.agilify.core.domain.enums.NotificationType;
import com.agilify.core.notifications.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void create(UUID recipientId, NotificationType type, String title, String body,
                       String referencedType, UUID referencedId) {
        notificationRepository.save(Notification.builder()
                .recipient(userRepository.getReferenceById(recipientId))
                .type(type)
                .title(title)
                .body(body)
                .referencedType(referencedType)
                .referencedId(referencedId)
                .build());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(UUID recipientId, boolean unreadOnly) {
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        return notifications.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void markAsRead(UUID recipientId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new NotFoundException("Notification not found with ID: " + notificationId));
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(UUID recipientId) {
        notificationRepository.markAllAsRead(recipientId);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                n.getReferencedType(), n.getReferencedId(), n.getIsRead(), n.getCreatedAt());
    }
}
