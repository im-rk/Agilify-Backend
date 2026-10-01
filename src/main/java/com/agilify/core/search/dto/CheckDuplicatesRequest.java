package com.agilify.core.search.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckDuplicatesRequest(
        @NotBlank(message = "draftText is required")
        String draftText
) {
}
