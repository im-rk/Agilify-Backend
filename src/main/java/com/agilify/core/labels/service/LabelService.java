package com.agilify.core.labels.service;

import com.agilify.core.common.exception.ConflictException;
import com.agilify.core.common.exception.NotFoundException;
import com.agilify.core.domain.entities.Issue;
import com.agilify.core.domain.entities.IssueLabel;
import com.agilify.core.domain.entities.IssueLabelId;
import com.agilify.core.domain.entities.Label;
import com.agilify.core.domain.entities.Project;
import com.agilify.core.issue.repository.IssueRepository;
import com.agilify.core.labels.dto.CreateLabelRequest;
import com.agilify.core.labels.dto.LabelResponse;
import com.agilify.core.labels.repository.IssueLabelRepository;
import com.agilify.core.labels.repository.LabelRepository;
import com.agilify.core.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LabelService {

    private final LabelRepository labelRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;

    @Transactional
    public LabelResponse create(String key, CreateLabelRequest request) {
        Project project = findProject(key);
        String name = request.name().trim();
        if (labelRepository.existsByProjectIdAndNameIgnoreCase(project.getId(), name)) {
            throw new ConflictException("Label '" + name + "' already exists in this project");
        }
        Label label = Label.builder().project(project).name(name).build();
        if (request.colour() != null) {
            label.setColour(request.colour());
        }
        return toResponse(labelRepository.save(label));
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> list(String key) {
        findProject(key);
        return labelRepository.findByProjectKeyOrderByNameAsc(normalize(key)).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(String key, UUID labelId) {
        Label label = findLabel(key, labelId);
        issueLabelRepository.deleteAllByLabelId(labelId);
        labelRepository.delete(label);
    }

    @Transactional
    public void attach(String key, UUID issueId, UUID labelId) {
        Issue issue = findIssue(key, issueId);
        Label label = findLabel(key, labelId);
        IssueLabelId id = new IssueLabelId(issueId, labelId);
        if (issueLabelRepository.existsById(id)) {
            return;
        }
        issueLabelRepository.save(IssueLabel.builder().id(id).issue(issue).label(label).build());
    }

    @Transactional
    public void detach(String key, UUID issueId, UUID labelId) {
        findIssue(key, issueId);
        findLabel(key, labelId);
        IssueLabelId id = new IssueLabelId(issueId, labelId);
        if (!issueLabelRepository.existsById(id)) {
            throw new NotFoundException("Label is not attached to this issue");
        }
        issueLabelRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> getIssueLabels(String key, UUID issueId) {
        findIssue(key, issueId);
        return issueLabelRepository.findLabelsByIssueId(issueId).stream().map(this::toResponse).toList();
    }

    private Project findProject(String key) {
        return projectRepository.findByKey(normalize(key))
                .orElseThrow(() -> new NotFoundException("Project not found with key: " + normalize(key)));
    }

    private Issue findIssue(String key, UUID issueId) {
        Issue issue = issueRepository.findByIdWithDetails(issueId)
                .orElseThrow(() -> new NotFoundException("Issue not found with ID: " + issueId));
        if (!issue.getProject().getKey().equals(normalize(key))) {
            throw new NotFoundException("Issue " + issueId + " does not belong to project " + normalize(key));
        }
        return issue;
    }

    private Label findLabel(String key, UUID labelId) {
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new NotFoundException("Label not found with ID: " + labelId));
        if (!label.getProject().getKey().equals(normalize(key))) {
            throw new NotFoundException("Label " + labelId + " does not belong to project " + normalize(key));
        }
        return label;
    }

    private String normalize(String key) {
        return key.trim().toUpperCase();
    }

    private LabelResponse toResponse(Label l) {
        return new LabelResponse(l.getId(), l.getName(), l.getColour());
    }
}
