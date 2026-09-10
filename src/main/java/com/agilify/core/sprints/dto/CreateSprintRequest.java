package com.agilify.core.sprints.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateSprintRequest(
        @NotBlank(message="Name is required")
        String name,

        String goal,

        @NotNull(message="Start date is required")
        LocalDate startDate,

        @NotNull(message="End date is required")
        @Future(message="End date must be in the future")
        LocalDate endDate
) {
}
