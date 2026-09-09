package com.agilify.core.issue.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateIssueStatusRequest (
    @NotBlank
    String status
)
{};