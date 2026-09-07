package com.agilify.core.project.dto;

import com.agilify.core.domain.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

public record UpdateProjectRequest(
        @NotBlank(message = "Project name is required")
        String name,

        String description,

        String iconUrl,

        @NotNull(message = "Status is required")
        ProjectStatus status
) {}