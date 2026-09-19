package com.agilify.core.labels.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateLabelRequest(
        @NotBlank(message = "Label name is required")
        @Size(max = 100)
        String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Colour must be a hex value like #3B82F6")
        String colour
) {
}
