package com.agilify.core.sprints.dto;

import com.agilify.core.domain.enums.SprintStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSprintStatusRequest(
        @NotNull(message = "Sprint status is required")
        SprintStatus status
) {
}
