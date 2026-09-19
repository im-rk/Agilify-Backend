package com.agilify.core.labels.repository;

import com.agilify.core.domain.entities.Label;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LabelRepository extends JpaRepository<Label, UUID> {

    List<Label> findByProjectKeyOrderByNameAsc(String projectKey);

    boolean existsByProjectIdAndNameIgnoreCase(UUID projectId, String name);
}
