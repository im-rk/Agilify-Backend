package com.agilify.core.docs.dto;

import jakarta.validation.constraints.Size;

public record CreateDocRequest(
        @Size(max = 500)
        String title
) {
}
