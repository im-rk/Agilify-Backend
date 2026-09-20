package com.agilify.core.board.service;

import com.agilify.core.board.dto.AssignIssueSprintRequest;
import com.agilify.core.board.dto.BoardResponse;
import com.agilify.core.domain.enums.SprintStatus;
import com.agilify.core.board.dto.IssueResponse;
import com.agilify.core.board.dto.UpdateIssueBoardPositionRequest;
import com.agilify.core.board.mapper.BoardMapper;
import com.agilify.core.common.exception.NotFoundException;
import com.agilify.core.domain.entities.Issue;
import com.agilify.core.domain.entities.Sprint;
import com.agilify.core.issue.repository.IssueRepository;
import com.agilify.core.project.repository.ProjectRepository;
import com.agilify.core.sprints.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardService {
    private final IssueRepository issueRepository;
    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final BoardMapper boardMapper;
    private final com.agilify.core.sprints.mapper.SprintMapper sprintMapper;
    private final com.agilify.core.issue.service.WorkflowGuardService workflowGuard;
    private final com.agilify.core.issue.service.IssueHistoryService historyService;

    @Transactional
    public void assignIssueToSprint(String projectKey, UUID issueId, AssignIssueSprintRequest request, UUID userId)
    {
        projectRepository.findByKey(projectKey.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Issue issue=issueRepository.findById(issueId)
                .filter(i -> i.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Issue not found"));

        if(!issue.getProject().getKey().equalsIgnoreCase(projectKey.trim()))
        {
            throw new IllegalArgumentException("Issue does not belong to the specified project");
        }

        if(request.sprintId()!=null)
        {
            Sprint sprint=sprintRepository.findById(request.sprintId())
                    .orElseThrow(() -> new IllegalArgumentException("Sprint not found"));

            if(!sprint.getProject().getKey().equalsIgnoreCase(projectKey.trim()))
            {
                throw new IllegalArgumentException("Sprint does not belong to the specified project");
            }
            if(sprint.getStatus() == com.agilify.core.domain.enums.SprintStatus.CLOSED)
            {
                throw new IllegalStateException("Cannot assign issues to a closed sprint");
            }
            historyService.recordChange(issue, userId, "sprint_id",
                    issue.getSprint() == null ? null : issue.getSprint().getId(), sprint.getId(), null);
            issue.setSprint(sprint);
        }
        else {
            historyService.recordChange(issue, userId, "sprint_id",
                    issue.getSprint() == null ? null : issue.getSprint().getId(), null, null);
            issue.setSprint(null);
        }
        issueRepository.save(issue);
    }

    @Transactional(readOnly = true)
    public List<IssueResponse> getProjectBacklog(String projectKey) {
        projectRepository.findByKey(projectKey.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectKey));

        List<Issue> backlogIssues = issueRepository.findByProjectKeyAndSprintIsNullAndDeletedAtIsNullOrderByBoardPositionAscCreatedAtAsc(projectKey.toUpperCase());

        String normalizedKey = projectKey.trim().toUpperCase();
        return backlogIssues.stream().map(i -> boardMapper.toIssueResponse(i, normalizedKey)).toList();
    }

    @Transactional(readOnly = true)
    public List<IssueResponse> getSprintBoardIssues(String projectKey, UUID sprintId) {
        String normalizedKey = projectKey.trim().toUpperCase();

        projectRepository.findByKey(normalizedKey)
                .orElseThrow(() -> new NotFoundException("Project not found with key: " + normalizedKey));

        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint not found with ID: " + sprintId));

        if (!sprint.getProject().getKey().equalsIgnoreCase(normalizedKey)) {
            throw new IllegalArgumentException("Sprint does not belong to project: " + normalizedKey);
        }

        List<Issue> sprintIssues = issueRepository.findBySprintIdAndDeletedAtIsNullOrderByBoardPositionAscCreatedAtAsc(sprintId);

        return sprintIssues.stream().map(i -> boardMapper.toIssueResponse(i, normalizedKey)).toList();
    }

    @Transactional
    public IssueResponse updateIssueBoardPosition(String projectKey, UUID issueId, UpdateIssueBoardPositionRequest request, UUID userId) {
        String normalizedKey = projectKey.trim().toUpperCase();

        projectRepository.findByKey(normalizedKey)
                .orElseThrow(() -> new NotFoundException("Project not found with key: " + normalizedKey));

        Issue issue = issueRepository.findById(issueId)
                .filter(i -> i.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Issue not found with ID: " + issueId));

        if (!issue.getProject().getKey().equalsIgnoreCase(normalizedKey)) {
            throw new IllegalArgumentException("Issue does not belong to project: " + normalizedKey);
        }

        if (request.sprintId() != null) {
            Sprint targetSprint = sprintRepository.findById(request.sprintId())
                    .orElseThrow(() -> new NotFoundException("Target sprint not found with ID: " + request.sprintId()));

            if (!targetSprint.getProject().getKey().equalsIgnoreCase(normalizedKey)) {
                throw new IllegalArgumentException("Target sprint does not belong to project: " + normalizedKey);
            }
            historyService.recordChange(issue, userId, "sprint_id",
                    issue.getSprint() == null ? null : issue.getSprint().getId(), targetSprint.getId(), null);
            issue.setSprint(targetSprint);
        }

        if (request.status() != null && !request.status().isBlank()) {
            String newStatus = request.status().trim().toUpperCase();
            workflowGuard.validate(issue, newStatus, userId);
            historyService.recordChange(issue, userId, "status", issue.getStatus(), newStatus, null);
            issue.setStatus(newStatus);
        }

        // ponytail: stores the requested slot as-is (no renumbering of neighbours); reindex here if gaps/duplicates matter
        if (request.position() != null) {
            issue.setBoardPosition(request.position());
        }

        Issue savedIssue = issueRepository.save(issue);

        return boardMapper.toIssueResponse(savedIssue, normalizedKey);
    }

    @Transactional(readOnly = true)
    public BoardResponse getActiveBoard(String projectKey) {
        String normalizedKey = projectKey.trim().toUpperCase();

        Sprint sprint = sprintRepository.findFirstByProjectKeyAndStatus(normalizedKey, SprintStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("No active sprint in project " + normalizedKey));

        List<IssueResponse> issues = issueRepository
                .findBySprintIdAndDeletedAtIsNullOrderByBoardPositionAscCreatedAtAsc(sprint.getId()).stream()
                .map(i -> boardMapper.toIssueResponse(i, normalizedKey))
                .toList();

        return new BoardResponse(sprintMapper.toResponse(sprint), issues);
    }
}
