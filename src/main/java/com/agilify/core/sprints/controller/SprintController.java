package com.agilify.core.sprints.controller;

import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.sprints.dto.*;
import com.agilify.core.sprints.service.SprintService;
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

@Tag(name = "Sprints", description = "Sprint planning, status transitions, and completion APIs")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;

    @Operation(summary = "Create a new sprint", description = "Creates a sprint for the given project. Requires DEVELOPER role.")
    @ApiResponse(responseCode = "201", description = "Sprint created successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PostMapping("/{key}/sprints")
    public ResponseEntity<SprintResponse> createSprint(
            @PathVariable String key,
            @Valid @RequestBody CreateSprintRequest request,
            @CurrentUser UUID userId) {

        SprintResponse response = sprintService.createSprint(key, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get project sprints", description = "Retrieves all sprints in a project.")
    @ApiResponse(responseCode = "200", description = "Sprints retrieved successfully")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping("/{key}/sprints")
    public ResponseEntity<List<SprintResponse>> getProjectSprints(
            @PathVariable String key,
            @CurrentUser UUID userId) {

        List<SprintResponse> sprints = sprintService.getProjectSprints(key);
        return ResponseEntity.ok(sprints);
    }

    @Operation(summary = "Get sprint by ID", description = "Retrieves a single sprint by its UUID.")
    @ApiResponse(responseCode = "200", description = "Sprint retrieved successfully")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping("/{key}/sprints/{sprintId}")
    public ResponseEntity<SprintResponse> getSprintById(
            @PathVariable String key,
            @PathVariable UUID sprintId,
            @CurrentUser UUID userId) {

        SprintResponse response = sprintService.getSprintById(key, sprintId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update sprint details", description = "Updates sprint name, goal, dates. Requires DEVELOPER role.")
    @ApiResponse(responseCode = "200", description = "Sprint updated successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PutMapping("/{key}/sprints/{sprintId}")
    public ResponseEntity<SprintResponse> updateSprint(
            @PathVariable String key,
            @PathVariable UUID sprintId,
            @Valid @RequestBody UpdateSprintRequest request,
            @CurrentUser UUID userId) {

        SprintResponse response = sprintService.updateSprint(key, sprintId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update sprint status", description = "Starts or modifies sprint status (e.g. PLANNED -> ACTIVE). Requires DEVELOPER role.")
    @ApiResponse(responseCode = "200", description = "Sprint status updated successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PatchMapping("/{key}/sprints/{sprintId}/status")
    public ResponseEntity<SprintResponse> updateSprintStatus(
            @PathVariable String key,
            @PathVariable UUID sprintId,
            @Valid @RequestBody UpdateSprintStatusRequest request,
            @CurrentUser UUID userId) {

        SprintResponse response = sprintService.updateSprintStatus(key, sprintId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete sprint", description = "Deletes a sprint. Requires DEVELOPER role.")
    @ApiResponse(responseCode = "204", description = "Sprint deleted successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @DeleteMapping("/{key}/sprints/{sprintId}")
    public ResponseEntity<Void> deleteSprint(
            @PathVariable String key,
            @PathVariable UUID sprintId,
            @CurrentUser UUID userId) {

        sprintService.deleteSprint(key, sprintId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Complete sprint", description = "Closes a sprint and moves incomplete issues to backlog or a next sprint. Requires DEVELOPER role.")
    @ApiResponse(responseCode = "200", description = "Sprint completed successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PostMapping("/{key}/sprints/{sprintId}/complete")
    public ResponseEntity<SprintResponse> completeSprint(
            @PathVariable String key,
            @PathVariable UUID sprintId,
            @RequestBody(required = false) CompleteSprintRequest request,
            @CurrentUser UUID userId) {

        SprintResponse response = sprintService.completeSprint(key, sprintId, request);
        return ResponseEntity.ok(response);
    }
}