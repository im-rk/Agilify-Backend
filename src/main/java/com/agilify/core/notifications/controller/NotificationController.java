package com.agilify.core.notifications.controller;

import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.notifications.dto.NotificationResponse;
import com.agilify.core.notifications.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Notifications", description = "The caller's own notifications, e.g. from watched issues")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "List my notifications", description = "Newest first; set unreadOnly=true to filter.")
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(notificationService.list(userId, unreadOnly));
    }

    @Operation(summary = "Mark one notification as read", description = "Only the recipient can mark their own notification.")
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id, @CurrentUser UUID userId) {
        notificationService.markAsRead(userId, id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Mark all my notifications as read")
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@CurrentUser UUID userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }
}
