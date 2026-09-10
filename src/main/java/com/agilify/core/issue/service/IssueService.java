package com.agilify.core.issue.service;

import com.agilify.core.auth.repository.UserRepository;
import com.agilify.core.common.exception.NotFoundException;
import com.agilify.core.domain.entities.Issue;
import com.agilify.core.domain.entities.Project;
import com.agilify.core.domain.entities.User;
import com.agilify.core.domain.enums.IssuePriority;
import com.agilify.core.issue.dto.*;
import com.agilify.core.issue.mapper.IssueMapper;
import com.agilify.core.issue.repository.IssueRepository;
import com.agilify.core.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.print.Pageable;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final IssueMapper issueMapper;

    @Transactional
    public IssueResponse createIssue(String key, CreateIssueRequest request, UUID reportedId)
    {
        String normalizedKey=key.trim().toUpperCase();

        Project project=projectRepository.findByKey(normalizedKey)
                .orElseThrow(()->new IllegalArgumentException("Project with key "+normalizedKey+" not found"));

        User reporter=userRepository.findById(reportedId)
                .orElseThrow(()->new IllegalArgumentException("Reporter with id "+reportedId+" not found"));

        User assignee=null;
        if(request.assigneeId()!=null)
        {
            assignee=userRepository.findById(request.assigneeId())
                    .orElseThrow(()->new IllegalArgumentException("Assignee with id "+request.assigneeId()+" not found"));
        }
        Integer maxNum=issueRepository.findMaxIssueNumberByProjectId(project.getId()).orElse(0);
        int nextIssueNumber=maxNum+1;
        Issue issue=Issue.builder()
                .project(project)
                .issueNumber(nextIssueNumber)
                .issueType(request.type())
                .title(request.title())
                .description(request.description())
                .priority(request.priority())
                .reporter(reporter)
                .assignee(assignee)
                .status("BACKLOG")
                .build();

        Issue savedIssue=issueRepository.save(issue);
        return issueMapper.toResponse(savedIssue);
    }


    @Transactional(readOnly = true)
    public List<IssueResponse> getProjectIssue(String key)
    {
        String noralizedKey=key.trim().toUpperCase();

        if(!projectRepository.existsByKey(noralizedKey))
        {
            throw new IllegalArgumentException("Project with key "+noralizedKey+" not found");
        }

        List<Issue> issues=issueRepository.findAllByProjectKeyWithDetails(noralizedKey);
        return issueMapper.toResponseList(issues);
    }

    @Transactional(readOnly = true)
    public IssueResponse getIssueById(String key, UUID id) {
        String normalizedKey = key.trim().toUpperCase();

        Issue issue = issueRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Issue not found with ID: " + id));

        if (!issue.getProject().getKey().equals(normalizedKey)) {
            throw new NotFoundException("Issue " + id + " does not belong to project " + normalizedKey);
        }

        return issueMapper.toResponse(issue);
    }

    @Transactional
    public IssueResponse updateIssueStatus(String key, UUID id, UpdateIssueStatusRequest request)
    {
        String normalizedkey=key.trim().toUpperCase();

        Issue issue=issueRepository.findByIdWithDetails(id)
                .orElseThrow(()->new NotFoundException("Issue not found with ID: "+id));

        if(!issue.getProject().getKey().equals(normalizedkey))
        {
            throw new NotFoundException("Issue"+id+"does not belong to this project");
        }
        issue.setStatus(request.status());

        if("DONE".equalsIgnoreCase(request.status()))
        {
            issue.setCompletedAt(java.time.Instant.now());
        }
        Issue updatedIssue=issueRepository.save(issue);
        return issueMapper.toResponse(updatedIssue);
    }

    @Transactional
    public IssueResponse assignIssue(String key, UUID id, AssignIssueRequest request) {
        String normalizedKey = key.trim().toUpperCase();

        Issue issue = issueRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Issue not found with ID: " + id));

        if (!issue.getProject().getKey().equals(normalizedKey)) {
            throw new NotFoundException("Issue " + id + " does not belong to project " + normalizedKey);
        }

        User assignee = null;
        if (request.assigneeId() != null) {
            assignee = userRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new NotFoundException("Assignee not found with ID: " + request.assigneeId()));
        }

        issue.setAssignee(assignee);
        Issue updatedIssue = issueRepository.save(issue);

        return issueMapper.toResponse(updatedIssue);
    }

    @Transactional
    public IssueResponse updateIssueDetails(String key, UUID id, UpdateIssueRequest request) {
        String normalizedKey = key.trim().toUpperCase();

        Issue issue = issueRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Issue not found with ID: " + id));

        if (!issue.getProject().getKey().equals(normalizedKey)) {
            throw new NotFoundException("Issue " + id + " does not belong to project " + normalizedKey);
        }

        issue.setTitle(request.title());
        issue.setDescription(request.description());
        issue.setPriority(request.priority());
        issue.setIssueType(request.type());

        Issue updatedIssue = issueRepository.save(issue);
        return issueMapper.toResponse(updatedIssue);
    }

    @Transactional
    public void deleteIssue(String key, UUID id) {
        String normalizedKey = key.trim().toUpperCase();

        Issue issue = issueRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Issue not found with ID: " + id));

        if (!issue.getProject().getKey().equals(normalizedKey)) {
            throw new NotFoundException("Issue " + id + " does not belong to project " + normalizedKey);
        }

        issue.setDeletedAt(java.time.Instant.now());
        issueRepository.save(issue);
    }

    @Transactional(readOnly = true)
    public Page<IssueResponse> getFilteredIssues(String key, String status, IssuePriority priority, Pageable pageable) {
        String normalizedKey = key.trim().toUpperCase();

        if (!projectRepository.existsByKey(normalizedKey)) {
            throw new NotFoundException("Project not found with key: " + normalizedKey);
        }

        Page<Issue> issuePage = issueRepository.findFilteredIssuesByProjectKey(
                normalizedKey, status, priority, pageable
        );

        return issuePage.map(issueMapper::toResponse);
    }
}
