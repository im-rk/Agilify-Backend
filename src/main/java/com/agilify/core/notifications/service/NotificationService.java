package com.agilify.core.notifications.service;

import com.agilify.core.notifications.repository.NotificationRepository;
import com.agilify.core.auth.repository.UserRepository;
import com.agilify.core.domain.entities.Notification;
import com.agilify.core.domain.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
