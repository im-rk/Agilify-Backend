package com.agilify.core.comments.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateCommentRequest(
        @NotBlank(message = "Content is required")
        String content,

        UUID parentCommentId  //only required if this comment is a reply to another comment
 ) {

}
