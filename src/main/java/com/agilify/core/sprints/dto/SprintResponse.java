package com.agilify.core.sprints.dto;

import com.agilify.core.domain.enums.SprintStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SprintResponse(
        UUID id,
        String name,
        String goal,
        SprintStatus status,
        LocalDate startDate,
        LocalDate endDate,
        Instant closedAt,
        UUID createdById
) {
}
