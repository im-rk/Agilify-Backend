package com.agilify.core.sprints.service;

import com.agilify.core.auth.repository.UserRepository;
import com.agilify.core.common.exception.NotFoundException;
import com.agilify.core.domain.entities.Issue;
import com.agilify.core.domain.entities.Project;
import com.agilify.core.domain.entities.Sprint;
import com.agilify.core.domain.entities.User;
import com.agilify.core.domain.enums.SprintStatus;
import com.agilify.core.issue.repository.IssueRepository;
import com.agilify.core.project.repository.ProjectRepository;
import com.agilify.core.sprints.dto.*;
import com.agilify.core.sprints.mapper.SprintMapper;
import com.agilify.core.sprints.repository.SprintRepository;
import org.springframework.transaction.annotation.Transactional; // Supports readOnly = true
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SprintMapper sprintMapper;
    private final IssueRepository issueRepository;


    @Transactional
    public SprintResponse createSprint(String key, CreateSprintRequest request, UUID userId) {
        String normalizedKey = key.trim().toUpperCase();

        Project project = projectRepository.findByKey(normalizedKey)
                .orElseThrow(() -> new NotFoundException("Project not found with key: " + normalizedKey));

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));

        Sprint sprint = Sprint.builder()
                .project(project)
                .name(request.name())
                .goal(request.goal())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(SprintStatus.PLANNED)
                .createdBy(creator)
                .build();

        Sprint savedSprint = sprintRepository.save(sprint);
        return sprintMapper.toResponse(savedSprint);
    }

    @Transactional(readOnly = true)
    public List<SprintResponse> getProjectSprints(String key) {
        String normalizedKey = key.trim().toUpperCase();

        if (!projectRepository.existsByKey(normalizedKey)) {
            throw new NotFoundException("Project not found with key: " + normalizedKey);
        }

        List<Sprint> sprints = sprintRepository.findAllByProjectKeyWithCreator(normalizedKey);
        return sprintMapper.toResponseList(sprints);
    }

    @Transactional(readOnly = true)
    public SprintResponse getSprintById(String key, UUID sprintId) {
        String normalizedKey = key.trim().toUpperCase();

        Sprint sprint = sprintRepository.findByIdWithCreator(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint not found with ID: " + sprintId));

        if (!sprint.getProject().getKey().equals(normalizedKey)) {
            throw new NotFoundException("Sprint " + sprintId + " does not belong to project " + normalizedKey);
        }

        return sprintMapper.toResponse(sprint);
    }

    @Transactional
    public SprintResponse updateSprint(String key, UUID sprintId, UpdateSprintRequest request) {
        String normalizedKey = key.trim().toUpperCase();

        Sprint sprint = sprintRepository.findByIdWithCreator(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint not found with ID: " + sprintId));

        if (!sprint.getProject().getKey().equals(normalizedKey)) {
            throw new NotFoundException("Sprint " + sprintId + " does not belong to project " + normalizedKey);
        }

        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new IllegalStateException("Only sprints in PLANNED status can be modified");
        }

        sprint.setName(request.name());
        sprint.setGoal(request.goal());
        sprint.setStartDate(request.startDate());
        sprint.setEndDate(request.endDate());

        Sprint updatedSprint = sprintRepository.save(sprint);
        return sprintMapper.toResponse(updatedSprint);
    }

    @Transactional
    public SprintResponse updateSprintStatus(String key, UUID sprintId, UpdateSprintStatusRequest request) {
        String normalizedKey = key.trim().toUpperCase();

        Sprint sprint = sprintRepository.findByIdWithCreator(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint not found with ID: " + sprintId));

        if (!sprint.getProject().getKey().equals(normalizedKey)) {
            throw new NotFoundException("Sprint " + sprintId + " does not belong to project " + normalizedKey);
        }

        SprintStatus newStatus = request.status();

        // If transitioning to ACTIVE, enforce that no other sprint is currently active
        if (newStatus == SprintStatus.ACTIVE && sprint.getStatus() != SprintStatus.ACTIVE) {
            boolean hasActiveSprint = sprintRepository.existsByProjectKeyAndStatus(normalizedKey, SprintStatus.ACTIVE);
            if (hasActiveSprint) {
                throw new IllegalStateException("Another sprint is already active in this project");
            }
        }

        sprint.setStatus(newStatus);

        if (newStatus == SprintStatus.CLOSED) {
            sprint.setClosedAt(java.time.Instant.now());
        }

        Sprint updatedSprint = sprintRepository.save(sprint);
        return sprintMapper.toResponse(updatedSprint);
    }

    @Transactional
    public void deleteSprint(String key, UUID sprintId) {
        String normalizedKey = key.trim().toUpperCase();

        Sprint sprint = sprintRepository.findByIdWithCreator(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint not found with ID: " + sprintId));

        if (!sprint.getProject().getKey().equals(normalizedKey)) {
            throw new NotFoundException("Sprint " + sprintId + " does not belong to project " + normalizedKey);
        }

        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new IllegalStateException("Only sprints in PLANNED status can be deleted");
        }

        sprintRepository.delete(sprint);
    }

    @Transactional
    public SprintResponse completeSprint(String projectKey, UUID sprintId, CompleteSprintRequest request) {
        String normalizedKey = projectKey.trim().toUpperCase();

        projectRepository.findByKey(normalizedKey)
                .orElseThrow(() -> new NotFoundException("Project not found with key: " + normalizedKey));

        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint not found with ID: " + sprintId));

        if (!sprint.getProject().getKey().equalsIgnoreCase(normalizedKey)) {
            throw new IllegalArgumentException("Sprint does not belong to project: " + normalizedKey);
        }

        sprint.setStatus(SprintStatus.CLOSED);


        Sprint savedSprint = sprintRepository.save(sprint);

        List<Issue> incompleteIssues = issueRepository.findBySprintIdAndStatusNot(sprintId, "DONE");

        Sprint rolloverSprint = null;
        if (request != null && request.rolloverSprintId() != null) {
            rolloverSprint = sprintRepository.findById(request.rolloverSprintId())
                    .orElseThrow(() -> new NotFoundException("Rollover target sprint not found with ID: " + request.rolloverSprintId()));

            if (!rolloverSprint.getProject().getKey().equalsIgnoreCase(normalizedKey)) {
                throw new IllegalArgumentException("Rollover sprint does not belong to this project");
            }
        }

        for (Issue issue : incompleteIssues) {
            issue.setSprint(rolloverSprint);
        }
        issueRepository.saveAll(incompleteIssues);

        return sprintMapper.toResponse(savedSprint);
    }
}