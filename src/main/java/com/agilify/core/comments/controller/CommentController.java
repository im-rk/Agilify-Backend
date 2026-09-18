package com.agilify.core.comments.controller;

import com.agilify.core.comments.dto.CommentResponse;
import com.agilify.core.comments.dto.CreateCommentRequest;
import com.agilify.core.comments.dto.UpdateCommentRequest;
import com.agilify.core.comments.service.CommentService;
import com.agilify.core.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Comments", description = "Issue threaded discussions and commenting APIs")
@RestController
@RequestMapping("/api/v1/issues/{issueId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "Add comment to issue", description = "Adds a root or threaded reply comment to an issue.")
    @ApiResponse(responseCode = "201", description = "Comment added successfully")
    @PreAuthorize("@commentSecurity.hasAccessByIssue(#issueId, #userId)")
    @PostMapping
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable UUID issueId,
            @Valid @RequestBody CreateCommentRequest request,
            @CurrentUser UUID userId) {

        CommentResponse response = commentService.addComment(issueId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @Operation(summary = "Get comments by issue", description = "Retrieves all comments associated with an issue in chronological order.")
    @ApiResponse(responseCode = "200", description = "Comments retrieved successfully")
    @PreAuthorize("@commentSecurity.hasAccessByIssue(#issueId, #userId)")
    @GetMapping
    public ResponseEntity<List<CommentResponse>> getCommentsByIssue(
            @PathVariable UUID issueId,
            @CurrentUser UUID userId) {

        List<CommentResponse> responses = commentService.getCommentsByIssue(issueId);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Update comment", description = "Updates comment content. Author or Admin only.")
    @ApiResponse(responseCode = "200", description = "Comment updated successfully")
    @PreAuthorize("@commentSecurity.isAuthorOrAdmin(#commentId, #userId)")
    @PutMapping("/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request,
            @CurrentUser UUID userId) {

        CommentResponse response = commentService.updateComment(commentId, request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete comment", description = "Soft-deletes a comment. Author or Admin only.")
    @ApiResponse(responseCode = "204", description = "Comment deleted successfully")
    @PreAuthorize("@commentSecurity.isAuthorOrAdmin(#commentId, #userId)")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID commentId,
            @CurrentUser UUID userId) {

        commentService.deleteComment(commentId, userId);
        return ResponseEntity.noContent().build();
    }
}