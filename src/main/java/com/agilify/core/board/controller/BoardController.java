package com.agilify.core.board.controller;

import com.agilify.core.board.dto.AssignIssueSprintRequest;
import com.agilify.core.board.dto.IssueResponse;
import com.agilify.core.board.service.BoardService;
import com.agilify.core.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class BoardController {
    private final BoardService boardService;

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

    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'VIEWER')")
    @GetMapping("/{key}/backlog")
    public ResponseEntity<List<IssueResponse>> getProjectBacklog(
            @PathVariable String key,
            @CurrentUser UUID userId) {

        List<IssueResponse> backlog = boardService.getProjectBacklog(key);
        return ResponseEntity.ok(backlog);
    }

    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @GetMapping("/{key}/sprints/{sprintId}/board")
    public ResponseEntity<List<IssueResponse>> getSprintBoardIssues(
            @PathVariable String key,
            @PathVariable UUID sprintId,
            @CurrentUser UUID userId) {

        List<IssueResponse> response = boardService.getSprintBoardIssues(key, sprintId);
        return ResponseEntity.ok(response);
    }
}
