package com.agilify.core.project.controller;

import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.project.dto.*;
import com.agilify.core.project.service.ProjectService;
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

@Tag(name = "Projects", description = "Project management and RBAC membership APIs")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "Create a new project", description = "Creates a project and automatically assigns the creator as ADMIN.")
    @ApiResponse(responseCode = "201", description = "Project created successfully")
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @CurrentUser UUID userId)
    {
        ProjectResponse projectResponse = projectService.createProject(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(projectResponse);
    }

    @Operation(summary = "Get user projects", description = "Retrieves all projects the authenticated user is a member of.")
    @ApiResponse(responseCode = "200", description = "List of user projects")
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getUserProjects(@CurrentUser UUID userId)
    {
        List<ProjectResponse> projects=projectService.getUserProjects(userId);
        return ResponseEntity.ok(projects);
    }

    @Operation(summary = "Get project by key", description = "Retrieves project details for a specific project key.")
    @ApiResponse(responseCode = "200", description = "Project details retrieved")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping("/{key}")
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable String key,
            @CurrentUser UUID userId
    )
    {
        ProjectResponse response=projectService.getProjectByKey(key);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update project", description = "Updates project details. Requires ADMIN role.")
    @ApiResponse(responseCode = "200", description = "Project updated successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'ADMIN')")
    @PutMapping("/{key}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable String key,
            @Valid @RequestBody UpdateProjectRequest request,
            @CurrentUser UUID userId
    )
    {
        ProjectResponse response = projectService.updateProject(key, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Add member to project", description = "Adds a new member with a specified role to the project. Requires ADMIN role.")
    @ApiResponse(responseCode = "201", description = "Member added successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'ADMIN')")
    @PostMapping("/{key}/members")
    public ResponseEntity<Void> addMember(
            @PathVariable String key,
            @Valid @RequestBody AddMemberRequest request,
            @CurrentUser UUID userId
    )
    {
        projectService.addMember(key,request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Remove member from project", description = "Removes a member from the project. Requires ADMIN role.")
    @ApiResponse(responseCode = "204", description = "Member removed successfully")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'ADMIN')")
    @DeleteMapping("/{key}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String key,
            @PathVariable UUID memberId,
            @CurrentUser UUID userId) {

        projectService.removeMember(key, memberId);
        return ResponseEntity.noContent().build();
    }
}
