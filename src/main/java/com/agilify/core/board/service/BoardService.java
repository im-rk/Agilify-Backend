package com.agilify.core.board.service;

import com.agilify.core.board.dto.AssignIssueSprintRequest;
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

    @Transactional
    public void assignIssueToSprint(String projectKey, UUID issueId, AssignIssueSprintRequest request)
    {
        projectRepository.findByKey(projectKey.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Issue issue=issueRepository.findById(issueId)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found"));

        if(request.sprintId()!=null)
        {
            Sprint sprint=sprintRepository.findById(request.sprintId())
                    .orElseThrow(() -> new IllegalArgumentException("Sprint not found"));

            if(!sprint.getProject().getKey().equalsIgnoreCase(projectKey))
            {
                throw new IllegalArgumentException("Sprint does not belong to the specified project");
            }
            issue.setSprint(sprint);
        }
        else {
            issue.setSprint(null);
        }
        issueRepository.save(issue);
    }

    @Transactional(readOnly = true)
    public List<IssueResponse> getProjectBacklog(String projectKey) {
        projectRepository.findByKey(projectKey.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectKey));

        List<Issue> backlogIssues = issueRepository.findByProjectKeyAndSprintIsNull(projectKey.toUpperCase());

        return backlogIssues.stream()
                .map(issue -> new IssueResponse(
                        issue.getId(),
                        projectKey.toUpperCase() + "-" + issue.getIssueNumber(),
                        issue.getTitle(),
                        issue.getDescription(),
                        issue.getStatus() != null ? issue.getStatus() : null,
                        issue.getPriority() != null ? issue.getPriority().name() : null, // IssuePriority is an Enum, use .name()
                        issue.getIssueType() != null ? issue.getIssueType().name() : null // IssueType is an Enum, use .name()
                ))
                .toList();
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

        List<Issue> sprintIssues = issueRepository.findBySprintId(sprintId);

        return sprintIssues.stream()
                .map(issue -> new IssueResponse(
                        issue.getId(),
                        normalizedKey + "-" + issue.getIssueNumber(),
                        issue.getTitle(),
                        issue.getDescription(),
                        issue.getStatus(),
                        issue.getPriority() != null ? issue.getPriority().name() : null,
                        issue.getIssueType() != null ? issue.getIssueType().name() : null
                ))
                .toList();
    }

    @Transactional
    public IssueResponse updateIssueBoardPosition(String projectKey, UUID issueId, UpdateIssueBoardPositionRequest request) {
        String normalizedKey = projectKey.trim().toUpperCase();

        projectRepository.findByKey(normalizedKey)
                .orElseThrow(() -> new NotFoundException("Project not found with key: " + normalizedKey));

        Issue issue = issueRepository.findById(issueId)
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
            issue.setSprint(targetSprint);
        }

        if (request.status() != null && !request.status().isBlank()) {
            issue.setStatus(request.status().trim().toUpperCase());
        }

        Issue savedIssue = issueRepository.save(issue);

        return boardMapper.toIssueResponse(savedIssue, normalizedKey);
    }
}
