package com.agilify.core.board.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateIssueBoardPositionRequest(
        UUID sprintId,
        @Size(min = 2, max = 50, message = "Status length must be between 2 and 50 characters")
        String status
) {}