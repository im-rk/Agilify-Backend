package com.agilify.core.project.repository;

import com.agilify.core.domain.entities.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    
    Optional<Project> findByKey(String key);
    boolean existsByKey(String key);
}
