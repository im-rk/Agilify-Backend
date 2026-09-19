package com.agilify.core.issue.service;

import com.agilify.core.auth.repository.UserRepository;
import com.agilify.core.common.exception.NotFoundException;
import com.agilify.core.domain.entities.Issue;
import com.agilify.core.domain.entities.IssueHistory;
import com.agilify.core.issue.dto.IssueHistoryResponse;
import com.agilify.core.issue.repository.IssueHistoryRepository;
import com.agilify.core.issue.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Append-only audit trail: this class only ever inserts and reads, never updates or deletes. */
@Service
@RequiredArgsConstructor
public class IssueHistoryService {

    private final IssueHistoryRepository historyRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;

    /** Joins the caller's transaction so the change and its audit row commit (or roll back) together. */
    @Transactional
    public void recordChange(Issue issue, UUID changedBy, String fieldName, Object oldValue, Object newValue, String reason) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        historyRepository.save(IssueHistory.builder()
                .issue(issue)
                .changedBy(userRepository.getReferenceById(changedBy))
                .fieldName(fieldName)
                .oldValue(oldValue == null ? null : oldValue.toString())
                .newValue(newValue == null ? null : newValue.toString())
                .changeReason(reason)
                .build());
    }

    @Transactional(readOnly = true)
    public List<IssueHistoryResponse> getHistory(String key, UUID issueId) {
        Issue issue = issueRepository.findByIdWithDetails(issueId)
                .orElseThrow(() -> new NotFoundException("Issue not found with ID: " + issueId));
        if (!issue.getProject().getKey().equalsIgnoreCase(key.trim())) {
            throw new NotFoundException("Issue " + issueId + " does not belong to project " + key);
        }
        return historyRepository.findByIssueIdOrderByChangedAtAsc(issueId).stream()
                .map(h -> new IssueHistoryResponse(h.getId(), h.getFieldName(), h.getOldValue(), h.getNewValue(),
                        h.getChangeReason(), h.getChangedBy().getId(), h.getChangedBy().getFullName(), h.getChangedAt()))
                .toList();
    }
}
