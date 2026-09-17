package com.agilify.core.comments.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID issueId,
        UUID authorId,
        String authorName,
        String authorAvatarUrl,
        UUID parentCommentId,
        String content,
        Boolean isEdited,
        Instant createdAt,
        Instant updatedAt
) {
}
