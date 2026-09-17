package com.agilify.core.sprints.dto;

import java.util.UUID;

public record CompleteSprintRequest(
        UUID rolloverSprintId
) {
}
