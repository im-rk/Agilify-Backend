package com.agilify.core.board.dto;

import java.util.UUID;

public record IssueResponse(
        UUID id,
        String issueKey,
        String title,
        String description,
        String status,
        String priority,
        String issueType
) {
}
