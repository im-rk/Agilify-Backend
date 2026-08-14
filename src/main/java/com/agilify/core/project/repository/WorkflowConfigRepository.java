package com.agilify.core.project.repository;

import com.agilify.core.domain.entities.WorkflowConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkflowConfigRepository extends JpaRepository<WorkflowConfig, UUID> {
}
