package com.agilify.core.issue.dto;

import java.time.Instant;
import java.util.UUID;

public record IssueHistoryResponse(
        UUID id,
        String fieldName,
        String oldValue,
        String newValue,
        String changeReason,
        UUID changedById,
        String changedByName,
        Instant changedAt
) {
}
