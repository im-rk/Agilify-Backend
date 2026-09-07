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

    @Query(value = "SELECT p.* FROM projects p JOIN project_members pm ON p.id = pm.project_id WHERE pm.user_id = :userId", nativeQuery = true)
    List<Project> findAllProjectByUserId(@Param("userId") UUID userId);

}
