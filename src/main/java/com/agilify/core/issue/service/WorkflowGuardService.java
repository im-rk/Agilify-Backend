package com.agilify.core.issue.service;

import com.agilify.core.common.exception.WorkflowTransitionException;
import com.agilify.core.common.security.ProjectSecurityService;
import com.agilify.core.domain.entities.Issue;
import com.agilify.core.project.repository.WorkflowConfigRepository;
import com.agilify.core.project.service.WorkflowSeeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowGuardService {

    private final WorkflowConfigRepository workflowConfigRepository;
    private final WorkflowSeeder workflowSeeder;
    private final ProjectSecurityService projectSecurity;

    @Transactional
    public void validate(Issue issue, String toState, UUID userId) {
        String fromState = issue.getStatus();
        if (fromState.equals(toState)) {
            return;
        }

        var project = issue.getProject();
        // projects created before workflows existed have no rows; give them the defaults once
        if (!workflowConfigRepository.existsByProjectId(project.getId())) {
            workflowSeeder.seedDefaults(project);
        }

        var rule = workflowConfigRepository
                .findByProjectIdAndIssueTypeAndFromStateAndToState(project.getId(), issue.getIssueType(), fromState, toState)
                .orElseThrow(() -> new WorkflowTransitionException(String.format(
                        "Transition '%s' -> '%s' is not allowed for %s issues in this project",
                        fromState, toState, issue.getIssueType())));

        if (rule.getRequiredRole() != null
                && !projectSecurity.hasRole(project.getKey(), userId.toString(), rule.getRequiredRole())) {
            throw new WorkflowTransitionException(String.format(
                    "Transition '%s' -> '%s' requires the %s role", fromState, toState, rule.getRequiredRole()));
        }
    }
}
