package com.agilify.core.domain.repositories;

import com.agilify.core.domain.entities.WorkflowConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WorkflowConfigRepository extends JpaRepository<WorkflowConfig, UUID> {
}
