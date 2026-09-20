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
    private final WorkflowSeeder workflowSeeder;

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
        workflowSeeder.seedDefaults(savedProject);

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
    public ProjectResponse updateProject(String key, UpdateProjectRequest request)
    {
        String normalizedKey=key.trim().toUpperCase();
        Project project=projectRepository.findByKey(normalizedKey)
                .orElseThrow(()->new NotFoundException("Project Not found with key: "+normalizedKey));

        projectMapper.updateProjectFromRequest(request,project);
        Project updatedProject=projectRepository.save(project);
        return projectMapper.toResponse(updatedProject);
    }

    @Transactional
    public void addMember(String key, AddMemberRequest request)
    {
        String normalizedKey=key.trim().toUpperCase();
        Project project=projectRepository.findByKey(normalizedKey)
                .orElseThrow(()->new NotFoundException("Project Not found with key: "+normalizedKey));

        // Check if the user is already a member
        if (projectMemberRepository.existsByProjectIdAndUserId(project.getId(), request.getUserId())) {
            throw new ConflictException("User is already a member of the project.");
        }

        User userToAdd = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + request.getUserId()));

        // Create and save the new ProjectMember
        ProjectMember newMember = ProjectMember.builder()
                .project(project)
                .user(userToAdd)
                .role(request.getRole())
                .build();

        projectMemberRepository.save(newMember);
    }

    @Transactional
    public void removeMember(String key, UUID memberId) {
        String normalizedKey = key.trim().toUpperCase();

        // 1. Find the project
        Project project = projectRepository.findByKey(normalizedKey)
                .orElseThrow(() -> new NotFoundException("Project not found with key: " + normalizedKey));

        // 2. Find the membership record
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(project.getId(), memberId)
                .orElseThrow(() -> new NotFoundException("User is not a member of this project"));

        // 3. Prevent removing the last ADMIN, which would leave the project unmanageable
        if (member.getRole() == ProjectRole.ADMIN
                && projectMemberRepository.countByProjectIdAndRole(project.getId(), ProjectRole.ADMIN) <= 1) {
            throw new ConflictException("Cannot remove the sole ADMIN of the project.");
        }

        // 4. Remove the member
        projectMemberRepository.delete(member);
    }
}
