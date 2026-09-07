package com.agilify.core.project.controller;

import com.agilify.core.common.response.ApiResponse;
import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.project.dto.*;
import com.agilify.core.project.service.ProjectService;
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
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @CurrentUser UUID userId)
    {
        ProjectResponse projectResponse = projectService.createProject(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(projectResponse);
    }

    public ResponseEntity<List<ProjectResponse>> getUserProjects(@CurrentUser UUID userId)
    {
        List<ProjectResponse> projects=projectService.getUserProjects(userId);
        return ResponseEntity.ok(projects);
    }

    @PreAuthorize("@projectSecurity.isMember(#key, #userId)")
    @GetMapping("/{key}")
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable String key,
            @CurrentUser UUID userId
    )
    {
        ProjectResponse response=projectService.getProjectByKey(key);
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable String key,
            @Valid @RequestBody UpdateProjectRequest request,
            @CurrentUser UUID userId
    )
    {

    }
}
