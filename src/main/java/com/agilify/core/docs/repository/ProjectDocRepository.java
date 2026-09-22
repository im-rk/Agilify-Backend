package com.agilify.core.docs.repository;

import com.agilify.core.domain.entities.ProjectDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDocRepository extends JpaRepository<ProjectDoc, UUID> {

    List<ProjectDoc> findByProjectKeyAndDeletedAtIsNullOrderByUpdatedAtDesc(String projectKey);

    @Query("SELECT d.project.key FROM ProjectDoc d WHERE d.id = :docId")
    Optional<String> findProjectKeyById(@Param("docId") UUID docId);
}
