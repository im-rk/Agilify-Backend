package com.agilify.core.board.controller;

import com.agilify.core.board.dto.AssignIssueSprintRequest;
import com.agilify.core.board.dto.IssueResponse;
import com.agilify.core.board.service.BoardService;
import com.agilify.core.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Board & Backlog", description = "Agile board visualization and sprint assignment APIs")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class BoardController {
    private final BoardService boardService;

    @Operation(summary = "Assign issue to sprint", description = "Moves an issue into or out of a sprint. Requires DEVELOPER role.")
    @ApiResponse(responseCode = "200", description = "Issue assigned to sprint successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PatchMapping("/{key}/issues/{issueId}/sprint")
    public ResponseEntity<Void> assignIssueToSprint(
            @PathVariable String key,
            @PathVariable UUID issueId,
            @Valid @RequestBody AssignIssueSprintRequest request,
            @CurrentUser UUID userId
    )
    {
        boardService.assignIssueToSprint(key,issueId,request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get project backlog", description = "Retrieves all unassigned/backlog issues for the project. Requires VIEWER role.")
    @ApiResponse(responseCode = "200", description = "Backlog issues retrieved")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'VIEWER')")
    @GetMapping("/{key}/backlog")
    public ResponseEntity<List<IssueResponse>> getProjectBacklog(
            @PathVariable String key,
            @CurrentUser UUID userId) {

        List<IssueResponse> backlog = boardService.getProjectBacklog(key);
        return ResponseEntity.ok(backlog);
    }

    @Operation(summary = "Get sprint board issues", description = "Retrieves active board columns and issues for a sprint. Requires VIEWER role.")
    @ApiResponse(responseCode = "200", description = "Sprint board issues retrieved")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'VIEWER')")
    @GetMapping("/{key}/sprints/{sprintId}/board")
    public ResponseEntity<List<IssueResponse>> getSprintBoardIssues(
            @PathVariable String key,
            @PathVariable UUID sprintId,
            @CurrentUser UUID userId) {

        List<IssueResponse> response = boardService.getSprintBoardIssues(key, sprintId);
        return ResponseEntity.ok(response);
    }
}
