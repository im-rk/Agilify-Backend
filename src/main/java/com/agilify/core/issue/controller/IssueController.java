package com.agilify.core.issue.controller;

import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.domain.enums.IssuePriority;
import com.agilify.core.issue.dto.*;
import com.agilify.core.issue.service.IssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.agilify.core.issue.service.IssueHistoryService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Issues", description = "Issue tracking, lifecycle, and backlog management APIs")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final IssueHistoryService historyService;
    private final com.agilify.core.search.IssueVectorService vectorService;

    @Operation(summary = "Get issue history", description = "Chronological audit trail of field changes. Requires project membership.")
    @ApiResponse(responseCode = "200", description = "History retrieved")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping("/{key}/issues/{id}/history")
    public ResponseEntity<List<IssueHistoryResponse>> getIssueHistory(
            @PathVariable String key,
            @PathVariable UUID id,
            @CurrentUser UUID userId
    )
    {
        return ResponseEntity.ok(historyService.getHistory(key, id));
    }

    @Operation(summary = "Create an issue", description = "Creates a new issue within the designated project. Requires DEVELOPER role.")
    @ApiResponse(responseCode = "201", description = "Issue created successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key,#userId.toString(), 'DEVELOPER')")
    @PostMapping("/{key}/issues")
    public ResponseEntity<IssueResponse> createIssue(
            @PathVariable String key,
            @Valid @RequestBody CreateIssueRequest issueRequest,
            @CurrentUser UUID userId
    )
    {
        IssueResponse response = issueService.createIssue(key, issueRequest, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get project issues", description = "Returns a paginated and filtered list of issues for a project. Requires project membership.")
    @ApiResponse(responseCode = "200", description = "Issues retrieved successfully")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping("/{key}/issues")
    public ResponseEntity<Page<IssueResponse>> getProjectIssues(
            @PathVariable String key,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) IssuePriority priority,
            Pageable pageable,
            @CurrentUser UUID userId
    )
    {
        return ResponseEntity.ok(issueService.getFilteredIssues(key, status, priority, pageable));
    }

    @Operation(summary = "Get issue by ID", description = "Retrieves issue details by its UUID.")
    @ApiResponse(responseCode = "200", description = "Issue retrieved successfully")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping("/{key}/issues/{id}")
    public ResponseEntity<IssueResponse> getIssueById(
            @PathVariable String key,
            @PathVariable UUID id,
            @CurrentUser UUID userId
    )
    {
        IssueResponse issue = issueService.getIssueById(key, id);
        return ResponseEntity.ok(issue);
    }

    @Operation(summary = "Update issue status", description = "Transitions an issue to a new status according to workflow rules. Requires DEVELOPER role.")
    @ApiResponse(responseCode = "200", description = "Status updated successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PatchMapping("/{key}/issues/{id}/status")
    public ResponseEntity<IssueResponse> updateIssueStatus(
            @PathVariable String key,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIssueStatusRequest request,
            @CurrentUser UUID userId
    )
    {
        IssueResponse response = issueService.updateIssueStatus(key, id, request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Assign issue", description = "Assigns an issue to a user. Requires DEVELOPER role.")
    @ApiResponse(responseCode = "200", description = "Issue assigned successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PatchMapping("/{key}/issues/{id}/assignee")
    public ResponseEntity<IssueResponse> assignIssue(
            @PathVariable String key,
            @PathVariable UUID id,
            @Valid @RequestBody AssignIssueRequest request,
            @CurrentUser UUID userId
    )
    {
        IssueResponse response = issueService.assignIssue(key, id, request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update issue details", description = "Updates title, description, priority, story points, due date, etc. Requires DEVELOPER role.")
    @ApiResponse(responseCode = "200", description = "Issue updated successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PutMapping("/{key}/issues/{id}")
    public ResponseEntity<IssueResponse> updateIssue(
            @PathVariable String key,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIssueRequest request,
            @CurrentUser UUID userId
    )
    {
        IssueResponse response = issueService.updateIssueDetails(key, id, request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Check for duplicate issues", description = "Semantic similarity search over existing issue titles/descriptions in this project. Requires DEVELOPER role (same as creating an issue).")
    @ApiResponse(responseCode = "200", description = "Possible duplicate issue IDs, most similar first")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PostMapping("/{key}/issues/check-duplicates")
    public ResponseEntity<List<UUID>> checkDuplicates(
            @PathVariable String key,
            @jakarta.validation.Valid @RequestBody com.agilify.core.search.dto.CheckDuplicatesRequest request,
            @CurrentUser UUID userId
    )
    {
        String normalizedKey = key.trim().toUpperCase();
        return ResponseEntity.ok(vectorService.findDuplicates(normalizedKey, request.draftText()));
    }

    @Operation(summary = "Delete issue", description = "Soft-deletes an issue. Requires DEVELOPER role.")
    @ApiResponse(responseCode = "204", description = "Issue deleted successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @DeleteMapping("/{key}/issues/{id}")
    public ResponseEntity<Void> deleteIssue(
            @PathVariable String key,
            @PathVariable UUID id,
            @CurrentUser UUID userId
    )
    {
        issueService.deleteIssue(key, id);
        return ResponseEntity.noContent().build();
    }
}
