package com.agilify.core.labels.controller;

import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.labels.dto.CreateLabelRequest;
import com.agilify.core.labels.dto.LabelResponse;
import com.agilify.core.labels.service.LabelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Labels", description = "Project labels and attaching them to issues")
@RestController
@RequestMapping("/api/v1/projects/{key}")
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;

    @Operation(summary = "Create a label", description = "Requires DEVELOPER role.")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PostMapping("/labels")
    public ResponseEntity<LabelResponse> create(
            @PathVariable String key,
            @Valid @RequestBody CreateLabelRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(labelService.create(key, request));
    }

    @Operation(summary = "List project labels", description = "Requires project membership.")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping("/labels")
    public ResponseEntity<List<LabelResponse>> list(@PathVariable String key, @CurrentUser UUID userId) {
        return ResponseEntity.ok(labelService.list(key));
    }

    @Operation(summary = "Delete a label", description = "Also detaches it from all issues. Requires ADMIN role.")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'ADMIN')")
    @DeleteMapping("/labels/{labelId}")
    public ResponseEntity<Void> delete(@PathVariable String key, @PathVariable UUID labelId, @CurrentUser UUID userId) {
        labelService.delete(key, labelId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Attach label to issue", description = "Idempotent. Requires DEVELOPER role.")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PostMapping("/issues/{issueId}/labels/{labelId}")
    public ResponseEntity<Void> attach(
            @PathVariable String key, @PathVariable UUID issueId, @PathVariable UUID labelId, @CurrentUser UUID userId) {
        labelService.attach(key, issueId, labelId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Detach label from issue", description = "Requires DEVELOPER role.")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @DeleteMapping("/issues/{issueId}/labels/{labelId}")
    public ResponseEntity<Void> detach(
            @PathVariable String key, @PathVariable UUID issueId, @PathVariable UUID labelId, @CurrentUser UUID userId) {
        labelService.detach(key, issueId, labelId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List labels on an issue", description = "Requires project membership.")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping("/issues/{issueId}/labels")
    public ResponseEntity<List<LabelResponse>> issueLabels(
            @PathVariable String key, @PathVariable UUID issueId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(labelService.getIssueLabels(key, issueId));
    }
}
