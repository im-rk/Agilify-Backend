package com.agilify.core.users.dto;

import java.util.UUID;

public record UserSummaryResponse(UUID id, String fullName, String email) {
}
