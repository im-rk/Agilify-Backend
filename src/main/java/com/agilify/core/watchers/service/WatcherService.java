package com.agilify.core.watchers.service;

import com.agilify.core.auth.repository.UserRepository;
import com.agilify.core.common.exception.NotFoundException;
import com.agilify.core.domain.entities.Issue;
import com.agilify.core.domain.entities.IssueWatcher;
import com.agilify.core.domain.entities.IssueWatcherId;
import com.agilify.core.domain.enums.NotificationType;
import com.agilify.core.issue.repository.IssueRepository;
import com.agilify.core.notifications.service.NotificationService;
import com.agilify.core.watchers.dto.WatcherResponse;
import com.agilify.core.watchers.repository.WatcherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WatcherService {

    private final WatcherRepository watcherRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public void watch(String key, UUID issueId, UUID userId) {
        addWatcher(findIssue(key, issueId), userId);
    }

    @Transactional
    public void addWatcher(Issue issue, UUID userId) {
        IssueWatcherId id = new IssueWatcherId(issue.getId(), userId);
        if (watcherRepository.existsById(id)) {
            return;
        }
        watcherRepository.save(IssueWatcher.builder()
                .id(id)
                .issue(issue)
                .user(userRepository.getReferenceById(userId))
                .build());
    }

    @Transactional
    public void unwatch(String key, UUID issueId, UUID userId) {
        findIssue(key, issueId);
        IssueWatcherId id = new IssueWatcherId(issueId, userId);
        if (watcherRepository.existsById(id)) {
            watcherRepository.deleteById(id);
        }
    }

    @Transactional(readOnly = true)
    public List<WatcherResponse> list(String key, UUID issueId) {
        findIssue(key, issueId);
        return watcherRepository.findByIssueId(issueId).stream()
                .map(w -> new WatcherResponse(w.getUser().getId(), w.getUser().getFullName(), w.getUser().getAvatarUrl()))
                .toList();
    }

    @Transactional
    public void notifyWatchers(Issue issue, UUID changedBy, String fieldName, String oldValue, String newValue) {
        NotificationType type = switch (fieldName) {
            case "status" -> NotificationType.ISSUE_STATUS_CHANGED;
            case "assignee_id" -> NotificationType.ISSUE_ASSIGNED;
            default -> NotificationType.ISSUE_UPDATED;
        };
        String issueKey = issue.getProject().getKey() + "-" + issue.getIssueNumber();
        String body = fieldName + " changed from " + (oldValue == null ? "none" : oldValue)
                + " to " + (newValue == null ? "none" : newValue);

        watcherRepository.findByIssueId(issue.getId()).stream()
                .map(w -> w.getUser().getId())
                .filter(recipient -> !recipient.equals(changedBy))
                .forEach(recipient -> notificationService.create(
                        recipient, type, issueKey + " updated", body, "ISSUE", issue.getId()));
    }

    private Issue findIssue(String key, UUID issueId) {
        Issue issue = issueRepository.findByIdWithDetails(issueId)
                .orElseThrow(() -> new NotFoundException("Issue not found with ID: " + issueId));
        if (!issue.getProject().getKey().equalsIgnoreCase(key.trim())) {
            throw new NotFoundException("Issue " + issueId + " does not belong to project " + key);
        }
        return issue;
    }
}
