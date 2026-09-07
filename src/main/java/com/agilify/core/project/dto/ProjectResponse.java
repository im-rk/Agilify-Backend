package com.agilify.core.project.dto;

import com.agilify.core.domain.enums.ProjectStatus;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String key,
        String description,
        String iconUrl,
        ProjectStatus status,
        UUID createdById, // Expose only the ID to the frontend, not the whole User object
        Instant createdAt,
        Instant updatedAt
) {}