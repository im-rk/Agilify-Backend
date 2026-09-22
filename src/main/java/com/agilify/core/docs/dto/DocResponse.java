package com.agilify.core.docs.dto;

import java.time.Instant;
import java.util.UUID;

public record DocResponse(
        UUID id,
        String title,
        UUID createdById,
        String createdByName,
        Instant createdAt,
        Instant updatedAt
) {
}
