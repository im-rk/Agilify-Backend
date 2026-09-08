package com.agilify.core.issue.dto;

import com.agilify.core.domain.enums.IssuePriority;
import com.agilify.core.domain.enums.IssueType;

import java.time.Instant;
import java.util.UUID;

public record IssueResponse(
        UUID id,
        String issueKey, // e.g., FOOD-1
        String title,
        String description,
        IssueType type,
        String status,
        IssuePriority priority,
        UUID assigneeId,
        String assigneeName,
        UUID reporterId,
        String reporterName,
        Instant createdAt,
        Instant updatedAt
) {
}
