package com.agilify.core.project.dto;

import java.util.UUID;

public record ProjectMemberResponse(
        UUID userId,
        String fullName,
        String email,
        String role
) {
}
