package com.agilify.core.issue.dto;

import com.agilify.core.domain.enums.IssuePriority;
import com.agilify.core.domain.enums.IssueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateIssueRequest(

        @NotBlank(message = "Issue title is required")
        String title,

        String description,

        @NotNull(message = "Issue type is required")
        IssueType type,

        @NotNull(message = "Priority is required")
        IssuePriority priority
) {
}
