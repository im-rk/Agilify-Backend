package com.agilify.core.project.service;

import com.agilify.core.common.exception.ConflictException;
import com.agilify.core.common.exception.ForbiddenException;
import com.agilify.core.common.exception.NotFoundException;
import com.agilify.core.domain.entities.Project;
import com.agilify.core.domain.entities.ProjectMember;
import com.agilify.core.domain.entities.User;
import com.agilify.core.domain.entities.WorkflowConfig;
import com.agilify.core.domain.enums.IssueType;
import com.agilify.core.domain.enums.ProjectRole;
import com.agilify.core.project.dto.AddMemberRequest;
import com.agilify.core.project.dto.CreateProjectRequest;
import com.agilify.core.project.dto.ProjectResponse;
import com.agilify.core.project.dto.UpdateProjectRequest;
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

    private final ProjectRepository projectRepo;
    private final ProjectMemberRepository memberRepo;
    private final WorkflowConfigRepository workflowRepo;
    private final ProjectMapper projectMapper;

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest req, UUID creatorId) {
        if (projectRepo.existsByKey(req.getKey())) {
            throw new ConflictException("Project key " + req.getKey() + " is already in use.");
        }

        User creatorRef = new User();
        creatorRef.setId(creatorId);

        Project project = Project.builder()
                .name(req.getName())
                .key(req.getKey().toUpperCase())
                .description(req.getDescription())
                .createdBy(creatorRef)
                .build();
        
        project = projectRepo.save(project);

        // Assign creator as ADMIN
        ProjectMember adminMember = ProjectMember.builder()
                .project(project)
                .user(creatorRef)
                .role(ProjectRole.ADMIN)
                .build();
        memberRepo.save(adminMember);

        seedDefaultWorkflows(project);

        return projectMapper.toResponse(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getMyProjects(UUID userId) {
        return projectRepo.findAllActiveByUserId(userId).stream()
                .map(projectMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectByKey(String key, UUID userId) {
        Project project = getProjectEntityByKey(key);
        verifyMembership(project.getId(), userId);
        return projectMapper.toResponse(project);
    }

    @Transactional
    public ProjectResponse updateProject(String key, UpdateProjectRequest req, UUID userId) {
        Project project = getProjectEntityByKey(key);
        verifyAdminAccess(project.getId(), userId);

        if (req.getName() != null) project.setName(req.getName());
        if (req.getDescription() != null) project.setDescription(req.getDescription());
        if (req.getIconUrl() != null) project.setIconUrl(req.getIconUrl());
        if (req.getStatus() != null) project.setStatus(req.getStatus());

        return projectMapper.toResponse(projectRepo.save(project));
    }

    @Transactional
    public void addMember(String key, AddMemberRequest req, UUID requesterId) {
        Project project = getProjectEntityByKey(key);
        verifyAdminAccess(project.getId(), requesterId);

        if (memberRepo.existsByProjectIdAndUserId(project.getId(), req.getUserId())) {
            throw new ConflictException("User is already a member of this project.");
        }

        User newUserRef = new User();
        newUserRef.setId(req.getUserId());

        ProjectMember newMember = ProjectMember.builder()
                .project(project)
                .user(newUserRef)
                .role(req.getRole())
                .build();

        memberRepo.save(newMember);
    }

    @Transactional
    public void deleteProject(String key, UUID userId) {
        Project project = getProjectEntityByKey(key);
        verifyAdminAccess(project.getId(), userId);

        project.setDeletedAt(java.time.LocalDateTime.now());
        projectRepo.save(project);
    }

    // --- Helper Methods ---

    private Project getProjectEntityByKey(String key) {
        return projectRepo.findByKeyAndDeletedAtIsNull(key.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Project not found: " + key));
    }

    private void verifyMembership(UUID projectId, UUID userId) {
        if (!memberRepo.existsByProjectIdAndUserId(projectId, userId)) {
            throw new ForbiddenException("You do not have access to this project.");
        }
    }

    private void verifyAdminAccess(UUID projectId, UUID userId) {
        ProjectMember member = memberRepo.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException("You do not have access to this project."));
        
        if (member.getRole() != ProjectRole.ADMIN) {
            throw new ForbiddenException("Only project ADMINs can perform this action.");
        }
    }

    private void seedDefaultWorkflows(Project project) {
        List<WorkflowConfig> configs = List.of(
            wf(project, IssueType.BUG, "BACKLOG", "TODO"),
            wf(project, IssueType.BUG, "TODO", "IN_PROGRESS"),
            wf(project, IssueType.BUG, "IN_PROGRESS", "IN_REVIEW"),
            wf(project, IssueType.BUG, "IN_REVIEW", "DONE"),
            wf(project, IssueType.BUG, "IN_REVIEW", "REJECTED"),
            wf(project, IssueType.BUG, "REJECTED", "IN_PROGRESS"),
            wf(project, IssueType.STORY, "BACKLOG", "TODO"),
            wf(project, IssueType.STORY, "TODO", "IN_PROGRESS"),
            wf(project, IssueType.STORY, "IN_PROGRESS", "DONE"),
            wf(project, IssueType.TASK, "TODO", "IN_PROGRESS"),
            wf(project, IssueType.TASK, "IN_PROGRESS", "DONE")
        );
        workflowRepo.saveAll(configs);
    }

    private WorkflowConfig wf(Project project, IssueType type, String from, String to) {
        return WorkflowConfig.builder()
                .project(project)
                .issueType(type)
                .fromState(from)
                .toState(to)
                .build();
    }
}
