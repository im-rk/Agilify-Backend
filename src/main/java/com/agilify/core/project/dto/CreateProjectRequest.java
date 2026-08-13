package com.agilify.core.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProjectRequest {
    @NotBlank(message = "Project name is required")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Project key is required")
    @Pattern(regexp = "^[A-Z]{2,10}$", message = "Key must be 2-10 uppercase letters (e.g., FOOD)")
    private String key;

    private String description;
}
