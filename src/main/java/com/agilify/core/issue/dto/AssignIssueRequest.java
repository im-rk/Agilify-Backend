package com.agilify.core.issue.dto;

import java.util.UUID;

public record AssignIssueRequest (
    UUID assigneeId
)
{
}