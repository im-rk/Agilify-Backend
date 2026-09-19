package com.agilify.core.issue.repository;

import com.agilify.core.domain.entities.IssueHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IssueHistoryRepository extends JpaRepository<IssueHistory, UUID> {

    @EntityGraph(attributePaths = {"changedBy"})
    List<IssueHistory> findByIssueIdOrderByChangedAtAsc(UUID issueId);
}
