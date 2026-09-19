package com.agilify.core.project.service;

import com.agilify.core.domain.entities.Project;
import com.agilify.core.domain.entities.WorkflowConfig;
import com.agilify.core.domain.enums.IssueType;
import com.agilify.core.project.repository.WorkflowConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkflowSeeder {

    private static final String[][] DEFAULT_TRANSITIONS = {
            {"BACKLOG", "TODO"},
            {"TODO", "BACKLOG"},
            {"TODO", "IN_PROGRESS"},
            {"IN_PROGRESS", "TODO"},
            {"IN_PROGRESS", "IN_REVIEW"},
            {"IN_REVIEW", "IN_PROGRESS"},
            {"IN_REVIEW", "DONE"},
            {"DONE", "TODO"} // reopen
    };

    private final WorkflowConfigRepository workflowConfigRepository;

    public void seedDefaults(Project project) {
        List<WorkflowConfig> rows = new ArrayList<>();
        for (IssueType type : IssueType.values()) {
            for (String[] t : DEFAULT_TRANSITIONS) {
                rows.add(WorkflowConfig.builder()
                        .project(project)
                        .issueType(type)
                        .fromState(t[0])
                        .toState(t[1])
                        .build());
            }
        }
        workflowConfigRepository.saveAll(rows);
    }
}
