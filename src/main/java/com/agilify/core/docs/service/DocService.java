package com.agilify.core.docs.service;

import com.agilify.core.auth.repository.UserRepository;
import com.agilify.core.common.exception.NotFoundException;
import com.agilify.core.domain.entities.Project;
import com.agilify.core.domain.entities.ProjectDoc;
import com.agilify.core.domain.entities.User;
import com.agilify.core.docs.dto.CreateDocRequest;
import com.agilify.core.docs.dto.DocResponse;
import com.agilify.core.docs.dto.UpdateDocTitleRequest;
import com.agilify.core.docs.repository.ProjectDocRepository;
import com.agilify.core.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocService {

    private final ProjectDocRepository docRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional
    public DocResponse create(String key, CreateDocRequest request, UUID userId) {
        Project project = findProject(key);
        User creator = userRepository.getReferenceById(userId);

        ProjectDoc.ProjectDocBuilder builder = ProjectDoc.builder()
                .project(project)
                .createdBy(creator);
        if (request.title() != null && !request.title().isBlank()) {
            builder.title(request.title().trim());
        }

        return toResponse(docRepository.save(builder.build()));
    }

    @Transactional(readOnly = true)
    public DocResponse getById(String key, UUID docId) {
        return toResponse(findDoc(key, docId));
    }

    @Transactional(readOnly = true)
    public List<DocResponse> list(String key) {
        findProject(key);
        return docRepository.findByProjectKeyAndDeletedAtIsNullOrderByUpdatedAtDesc(normalize(key))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public DocResponse updateTitle(String key, UUID docId, UpdateDocTitleRequest request) {
        ProjectDoc doc = findDoc(key, docId);
        doc.setTitle(request.title().trim());
        return toResponse(docRepository.save(doc));
    }

    @Transactional
    public void delete(String key, UUID docId) {
        ProjectDoc doc = findDoc(key, docId);
        doc.setDeletedAt(Instant.now());
        docRepository.save(doc);
    }

    /** Used by the WebSocket handshake interceptor to check project membership before accepting a connection. */
    @Transactional(readOnly = true)
    public String getProjectKeyForDoc(UUID docId) {
        return docRepository.findProjectKeyById(docId).orElse(null);
    }

    private ProjectDoc findDoc(String key, UUID docId) {
        ProjectDoc doc = docRepository.findById(docId)
                .filter(d -> d.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Doc not found with ID: " + docId));
        if (!doc.getProject().getKey().equals(normalize(key))) {
            throw new NotFoundException("Doc " + docId + " does not belong to project " + key);
        }
        return doc;
    }

    private Project findProject(String key) {
        return projectRepository.findByKey(normalize(key))
                .orElseThrow(() -> new NotFoundException("Project not found with key: " + normalize(key)));
    }

    private String normalize(String key) {
        return key.trim().toUpperCase();
    }

    private DocResponse toResponse(ProjectDoc d) {
        return new DocResponse(d.getId(), d.getTitle(), d.getCreatedBy().getId(), d.getCreatedBy().getFullName(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
