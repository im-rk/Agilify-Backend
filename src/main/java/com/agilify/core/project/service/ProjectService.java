package com.agilify.core.project.service;

import com.agilify.core.auth.repository.UserRepository;
import com.agilify.core.common.exception.ConflictException;
import com.agilify.core.common.exception.ForbiddenException;
import com.agilify.core.common.exception.NotFoundException;
import com.agilify.core.domain.entities.Project;
import com.agilify.core.domain.entities.ProjectMember;
import com.agilify.core.domain.entities.User;
import com.agilify.core.domain.entities.WorkflowConfig;
import com.agilify.core.domain.enums.IssueType;
import com.agilify.core.domain.enums.ProjectRole;
import com.agilify.core.project.dto.*;
import com.agilify.core.project.mapper.ProjectMapper;
import com.agilify.core.project.repository.ProjectMemberRepository;
import com.agilify.core.project.repository.ProjectRepository;
import com.agilify.core.project.repository.WorkflowConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMapper projectMapper;
    private final UserRepository userRepository;

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request,UUID creatorId)
    {
        String normalizedKey = request.getKey().trim().toUpperCase();

        if (projectRepository.existsByKey(normalizedKey)) {
            throw new IllegalArgumentException("Project key already exists: " + normalizedKey);
        }

        // Get a proxy reference to the User without running a DB query
        User creator = userRepository.getReferenceById(creatorId);

        // 1. Build and save the Project
        Project project = Project.builder()
                .name(request.getName().trim())
                .key(normalizedKey)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .createdBy(creator) // Pass the User entity
                // status and iconUrl are handled by defaults or left null
                .build();

        Project savedProject = projectRepository.save(project);

        // 2. Build and save the Creator as ADMIN in ProjectMember
        ProjectMember adminMember = ProjectMember.builder()
                .project(savedProject) // Pass the Project entity
                .user(creator)         // Pass the User entity
                .role(ProjectRole.ADMIN)
                .build();

        projectMemberRepository.save(adminMember);

        return projectMapper.toResponse(savedProject);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getUserProjects(UUID userId)
    {
        List<Project> userProjects=projectRepository.findAllProjectByUserId(userId);
        return projectMapper.toResponseList(userProjects);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectByKey(String key)
    {
        String normalizedKey=key.trim().toUpperCase();
        Project project=projectRepository.findByKey(normalizedKey)
                .orElseThrow(()->new NotFoundException("Project not found with key: "+normalizedKey));
        return projectMapper.toResponse(project);
    }

    @Transactional
    public ProjectResponse updateProject(String key, UpdateProjectRequest request, UUID userId)
    {
        String normalizedKey=key.trim().toUpperCase();
        Project project=projectRepository.findByKey(normalizedKey)
                .orElseThrow(()->new NotFoundException("Project Not found with key: "+normalizedKey));

        projectMapper.updateProjectFromRequest(request,project);
        Project updatedProject=projectRepository.save(project);
        return projectMapper.toResponse(updatedProject);
    }

}
