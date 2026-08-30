package com.agilify.core.project.controller;

import com.agilify.core.common.response.ApiResponse;
import com.agilify.core.project.dto.*;
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
            UUID userId) {
        return ApiResponse.created(projectService.createProject(request, userId));
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> getMyProjects(UUID userId) {
        return ApiResponse.ok(projectService.getMyProjects(userId));
    }

    @GetMapping("/{key}")
    public ApiResponse<ProjectResponse> getProject(
            @PathVariable String key, 
            UUID userId) {
        return ApiResponse.ok(projectService.getProjectByKey(key, userId));
    }

    @PatchMapping("/{key}")
    public ApiResponse<ProjectResponse> updateProject(
            @PathVariable String key,
            @Valid @RequestBody UpdateProjectRequest request,
            UUID userId) {
        return ApiResponse.ok(projectService.updateProject(key, request, userId));
    }

    @PostMapping("/{key}/members")
    public ApiResponse<Void> addMember(
            @PathVariable String key,
            @Valid @RequestBody AddMemberRequest request,
            UUID userId) {
        projectService.addMember(key, request, userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{key}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable String key,
            @PathVariable UUID userId,
            UUID currentUserId) {
        projectService.removeMember(key, userId, currentUserId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{key}/members")
    public ApiResponse<List<ProjectMemberResponse>> getProjectMembers(
            @PathVariable String key,
            UUID userId) {
        return ApiResponse.ok(projectService.getProjectMembers(key, userId));
    }

    @DeleteMapping("/{key}")
    public ApiResponse<Void> deleteProject(
            @PathVariable String key,
            UUID userId) {
        projectService.deleteProject(key, userId);
        return ApiResponse.ok(null);
    }
}
