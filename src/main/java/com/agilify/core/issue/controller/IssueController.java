package com.agilify.core.issue.controller;

import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.issue.dto.CreateIssueRequest;
import com.agilify.core.issue.dto.IssueResponse;
import com.agilify.core.issue.dto.UpdateIssueStatusRequest;
import com.agilify.core.issue.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

}
