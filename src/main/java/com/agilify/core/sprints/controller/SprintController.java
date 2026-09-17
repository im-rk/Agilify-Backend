package com.agilify.core.sprints.controller;

import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.sprints.dto.*;
import com.agilify.core.sprints.service.SprintService;
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
public class SprintController {

    private final SprintService sprintService;

    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PostMapping("/{key}/sprints")
    public ResponseEntity<SprintResponse> createSprint(
            @PathVariable String key,
            @Valid @RequestBody CreateSprintRequest request,
            @CurrentUser UUID userId) {

        SprintResponse response = sprintService.createSprint(key, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping("/{key}/sprints")
    public ResponseEntity<List<SprintResponse>> getProjectSprints(
            @PathVariable String key,
            @CurrentUser UUID userId) {

        List<SprintResponse> sprints = sprintService.getProjectSprints(key);
        return ResponseEntity.ok(sprints);
    }

    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping("/{key}/sprints/{sprintId}")
    public ResponseEntity<SprintResponse> getSprintById(
            @PathVariable String key,
            @PathVariable UUID sprintId,
            @CurrentUser UUID userId) {

        SprintResponse response = sprintService.getSprintById(key, sprintId);
        return ResponseEntity.ok(response);
    }

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

    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @DeleteMapping("/{key}/sprints/{sprintId}")
    public ResponseEntity<Void> deleteSprint(
            @PathVariable String key,
            @PathVariable UUID sprintId,
            @CurrentUser UUID userId) {

        sprintService.deleteSprint(key, sprintId);
        return ResponseEntity.noContent().build();
    }

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