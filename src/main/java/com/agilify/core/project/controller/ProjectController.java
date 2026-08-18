package com.agilify.core.project.controller;

import com.agilify.core.common.response.ApiResponse;
import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.project.dto.AddMemberRequest;
import com.agilify.core.project.dto.CreateProjectRequest;
import com.agilify.core.project.dto.ProjectResponse;
import com.agilify.core.project.dto.UpdateProjectRequest;
import com.agilify.core.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @CurrentUser UUID userId) {
        return ApiResponse.created(projectService.createProject(request, userId));
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> getMyProjects(@CurrentUser UUID userId) {
        return ApiResponse.ok(projectService.getMyProjects(userId));
    }

    @GetMapping("/{key}")
    public ApiResponse<ProjectResponse> getProject(
            @PathVariable String key, 
            @CurrentUser UUID userId) {
        return ApiResponse.ok(projectService.getProjectByKey(key, userId));
    }

    @PatchMapping("/{key}")
    public ApiResponse<ProjectResponse> updateProject(
            @PathVariable String key,
            @Valid @RequestBody UpdateProjectRequest request,
            @CurrentUser UUID userId) {
        return ApiResponse.ok(projectService.updateProject(key, request, userId));
    }

    @PostMapping("/{key}/members")
    public ApiResponse<Void> addMember(
            @PathVariable String key,
            @Valid @RequestBody AddMemberRequest request,
            @CurrentUser UUID userId) {
        projectService.addMember(key, request, userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{key}")
    public ApiResponse<Void> deleteProject(
            @PathVariable String key,
            @CurrentUser UUID userId) {
        projectService.deleteProject(key, userId);
        return ApiResponse.ok(null);
    }
}
