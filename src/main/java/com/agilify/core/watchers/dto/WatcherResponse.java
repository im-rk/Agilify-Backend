package com.agilify.core.watchers.dto;

import java.util.UUID;

public record WatcherResponse(UUID userId, String fullName, String avatarUrl) {
}
