package com.agilify.core.docs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDocTitleRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 500)
        String title
) {
}
