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

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final IssueMapper issueMapper;
    private final WorkflowGuardService workflowGuard;
    private final IssueHistoryService historyService;
    private final com.agilify.core.project.repository.ProjectMemberRepository memberRepository;
    private final com.agilify.core.watchers.service.WatcherService watcherService;
    private final com.agilify.core.search.IssueVectorService vectorService;

    @Transactional
    public IssueResponse createIssue(String key, CreateIssueRequest request, UUID reportedId)
    {
        String normalizedKey=key.trim().toUpperCase();

        // row lock serialises concurrent creates so MAX(issue_number)+1 cannot collide
        Project project=projectRepository.findByKeyForUpdate(normalizedKey)
                .orElseThrow(()->new IllegalArgumentException("Project with key "+normalizedKey+" not found"));

        User reporter=userRepository.findById(reportedId)
                .orElseThrow(()->new IllegalArgumentException("Reporter with id "+reportedId+" not found"));

        User assignee=null;
        if(request.assigneeId()!=null)
        {
            assignee=userRepository.findById(request.assigneeId())
                    .orElseThrow(()->new IllegalArgumentException("Assignee with id "+request.assigneeId()+" not found"));
            requireProjectMember(project.getId(), assignee.getId());
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
                .storyPoints(request.storyPoints())
                .dueDate(request.dueDate())
                .reporter(reporter)
                .assignee(assignee)
                .status("BACKLOG")
                .build();

        Issue savedIssue=issueRepository.save(issue);
        watcherService.addWatcher(savedIssue, reporter.getId());
        if(assignee!=null)
        {
            watcherService.addWatcher(savedIssue, assignee.getId());
        }
        vectorService.indexIssue(savedIssue.getId(), normalizedKey, savedIssue.getTitle(), savedIssue.getDescription());
        return issueMapper.toResponse(savedIssue);
    }

    private void requireProjectMember(UUID projectId, UUID userId)
    {
        if(!memberRepository.existsByProjectIdAndUserId(projectId, userId))
        {
            throw new IllegalArgumentException("Assignee must be a member of the project");
        }
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
    public IssueResponse updateIssueStatus(String key, UUID id, UpdateIssueStatusRequest request, UUID userId)
    {
        String normalizedkey=key.trim().toUpperCase();

        Issue issue=issueRepository.findByIdWithDetails(id)
                .orElseThrow(()->new NotFoundException("Issue not found with ID: "+id));

        if(!issue.getProject().getKey().equals(normalizedkey))
        {
            throw new NotFoundException("Issue"+id+"does not belong to this project");
        }
        String newStatus=request.status().trim().toUpperCase();
        workflowGuard.validate(issue, newStatus, userId);
        historyService.recordChange(issue, userId, "status", issue.getStatus(), newStatus, null);
        issue.setStatus(newStatus);

        if("DONE".equals(newStatus))
        {
            issue.setCompletedAt(java.time.Instant.now());
        }
        else
        {
            issue.setCompletedAt(null);
        }
        Issue updatedIssue=issueRepository.save(issue);
        return issueMapper.toResponse(updatedIssue);
    }

    @Transactional
    public IssueResponse assignIssue(String key, UUID id, AssignIssueRequest request, UUID userId) {
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
            requireProjectMember(issue.getProject().getId(), assignee.getId());
            watcherService.addWatcher(issue, assignee.getId());
        }

        historyService.recordChange(issue, userId, "assignee_id",
                issue.getAssignee() == null ? null : issue.getAssignee().getId(),
                assignee == null ? null : assignee.getId(), null);
        issue.setAssignee(assignee);
        Issue updatedIssue = issueRepository.save(issue);

        return issueMapper.toResponse(updatedIssue);
    }

    @Transactional
    public IssueResponse updateIssueDetails(String key, UUID id, UpdateIssueRequest request, UUID userId) {
        String normalizedKey = key.trim().toUpperCase();

        Issue issue = issueRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Issue not found with ID: " + id));

        if (!issue.getProject().getKey().equals(normalizedKey)) {
            throw new NotFoundException("Issue " + id + " does not belong to project " + normalizedKey);
        }

        historyService.recordChange(issue, userId, "title", issue.getTitle(), request.title(), null);
        historyService.recordChange(issue, userId, "description", issue.getDescription(), request.description(), null);
        historyService.recordChange(issue, userId, "priority", issue.getPriority(), request.priority(), null);
        historyService.recordChange(issue, userId, "issue_type", issue.getIssueType(), request.type(), null);
        historyService.recordChange(issue, userId, "story_points", issue.getStoryPoints(), request.storyPoints(), null);
        historyService.recordChange(issue, userId, "due_date", issue.getDueDate(), request.dueDate(), null);
        issue.setStoryPoints(request.storyPoints());
        issue.setDueDate(request.dueDate());
        issue.setTitle(request.title());
        issue.setDescription(request.description());
        issue.setPriority(request.priority());
        issue.setIssueType(request.type());

        Issue updatedIssue = issueRepository.save(issue);
        vectorService.indexIssue(updatedIssue.getId(), normalizedKey, updatedIssue.getTitle(), updatedIssue.getDescription());
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
