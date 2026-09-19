package com.agilify.core.project.repository;

import com.agilify.core.domain.entities.WorkflowConfig;
import com.agilify.core.domain.enums.IssueType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowConfigRepository extends JpaRepository<WorkflowConfig, UUID> {

    Optional<WorkflowConfig> findByProjectIdAndIssueTypeAndFromStateAndToState(
            UUID projectId, IssueType issueType, String fromState, String toState);

    boolean existsByProjectId(UUID projectId);
}
