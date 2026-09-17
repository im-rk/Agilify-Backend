package com.agilify.core.comments.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCommentRequest(
        @NotBlank(message = "Comment content cannot be blank")
        String content
) {}
