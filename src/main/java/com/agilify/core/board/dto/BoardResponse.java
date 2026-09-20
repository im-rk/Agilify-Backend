package com.agilify.core.board.dto;

import com.agilify.core.sprints.dto.SprintResponse;

import java.util.List;

public record BoardResponse(SprintResponse sprint, List<IssueResponse> issues) {
}
