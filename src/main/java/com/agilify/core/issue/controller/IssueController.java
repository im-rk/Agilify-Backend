package com.agilify.core.issue.controller;

import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.domain.enums.IssuePriority;
import com.agilify.core.issue.dto.*;
import com.agilify.core.issue.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.awt.print.Pageable;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @PreAuthorize("@projectSecurity.hasRole(#key,#userId.toString(), 'DEVELOPER')")
    @PostMapping("/{key}/issues")
    public ResponseEntity<IssueResponse> createIssue(
            @PathVariable String key,
            @Valid @RequestBody CreateIssueRequest issueRequest,
            @CurrentUser UUID userId
    )
    {
        IssueResponse response=issueService.createIssue(key, issueRequest, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @PreAuthorize("@projectSecurity.hasRole(#key,#userId.toString(), 'VIEWER')")
    @GetMapping("/{key}/issues/issues")
    public ResponseEntity<List<IssueResponse>> getIssue(
            @PathVariable String key,
            @CurrentUser UUID userId
    )
    {
        List<IssueResponse> issues=issueService.getProjectIssue(key);
        return ResponseEntity.ok(issues);
    }

    @PreAuthorize("@projectSecurity.hasRole(#key,#userId.toString())")
    @GetMapping("/{key}/issues/{id}")
    public ResponseEntity<IssueResponse> getIssueById(
            @PathVariable String key,
            @PathVariable UUID id,
            @CurrentUser UUID userId
    )
    {
        IssueResponse issue=issueService.getIssueById(key, id);
        return ResponseEntity.ok(issue);
    }

    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PatchMapping("/projects/{key}/issues/{id}/status")
    public ResponseEntity<IssueResponse> updateIssueStatus(
            @PathVariable String key,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIssueStatusRequest request,
            @CurrentUser UUID userId) {

        IssueResponse response = issueService.updateIssueStatus(key, id, request);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PatchMapping("/projects/{key}/issues/{id}/assignee")
    public ResponseEntity<IssueResponse> assignIssue(
            @PathVariable String key,
            @PathVariable UUID id,
            @Valid @RequestBody AssignIssueRequest request,
            @CurrentUser UUID userId) {

        IssueResponse response = issueService.assignIssue(key, id, request);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PutMapping("/projects/{key}/issues/{id}")
    public ResponseEntity<IssueResponse> updateIssue(
            @PathVariable String key,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIssueRequest request,
            @CurrentUser UUID userId) {

        IssueResponse response = issueService.updateIssueDetails(key, id, request);
        return ResponseEntity.ok(response);
    }


    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @DeleteMapping("/projects/{key}/issues/{id}")
    public ResponseEntity<Void> deleteIssue(
            @PathVariable String key,
            @PathVariable UUID id,
            @CurrentUser UUID userId) {

        issueService.deleteIssue(key, id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping("/projects/{key}/issues")
    public ResponseEntity<Page<IssueResponse>> getProjectIssues(
            @PathVariable String key,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) IssuePriority priority,
            Pageable pageable,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(issueService.getFilteredIssues(key, status, priority, pageable));
    }

}
