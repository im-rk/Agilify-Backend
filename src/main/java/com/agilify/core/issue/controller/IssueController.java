package com.agilify.core.issue.controller;

import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.issue.dto.CreateIssueRequest;
import com.agilify.core.issue.dto.IssueResponse;
import com.agilify.core.issue.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/issues")
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
}
