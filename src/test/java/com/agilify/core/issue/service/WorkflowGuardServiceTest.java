package com.agilify.core.issue.service;

import com.agilify.core.common.exception.WorkflowTransitionException;
import com.agilify.core.common.security.ProjectSecurityService;
import com.agilify.core.domain.entities.Issue;
import com.agilify.core.domain.entities.Project;
import com.agilify.core.domain.entities.WorkflowConfig;
import com.agilify.core.domain.enums.IssueType;
import com.agilify.core.project.repository.WorkflowConfigRepository;
import com.agilify.core.project.service.WorkflowSeeder;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowGuardServiceTest {

    private final WorkflowConfigRepository repo = mock(WorkflowConfigRepository.class);
    private final WorkflowGuardService guard =
            new WorkflowGuardService(repo, mock(WorkflowSeeder.class), mock(ProjectSecurityService.class));

    private Issue issue(String status) {
        Project project = Project.builder().id(UUID.randomUUID()).key("FOOD").build();
        return Issue.builder().project(project).issueType(IssueType.TASK).status(status).build();
    }

    @Test
    void allowedTransitionPasses_disallowedIsRejected() {
        when(repo.existsByProjectId(any())).thenReturn(true);
        when(repo.findByProjectIdAndIssueTypeAndFromStateAndToState(any(), eq(IssueType.TASK), eq("TODO"), eq("IN_PROGRESS")))
                .thenReturn(Optional.of(new WorkflowConfig()));
        when(repo.findByProjectIdAndIssueTypeAndFromStateAndToState(any(), eq(IssueType.TASK), eq("DONE"), eq("IN_PROGRESS")))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> guard.validate(issue("TODO"), "IN_PROGRESS", UUID.randomUUID()));
        assertThrows(WorkflowTransitionException.class,
                () -> guard.validate(issue("DONE"), "IN_PROGRESS", UUID.randomUUID()));
    }
}
