package com.agilify.core.project.repository;

import com.agilify.core.domain.entities.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    
    @Query(value = "SELECT EXISTS(SELECT 1 FROM projects WHERE key = :key)", nativeQuery = true)
    boolean existsByKey(@Param("key") String key);

    @Query(value = "SELECT * FROM projects WHERE key = :key AND deleted_at IS NULL", nativeQuery = true)
    Optional<Project> findByKeyAndDeletedAtIsNull(@Param("key") String key);

    @Query(value = """
        SELECT p.* FROM projects p 
        JOIN project_members pm ON p.id = pm.project_id 
        WHERE pm.user_id = :userId AND p.deleted_at IS NULL
    """, nativeQuery = true)
    List<Project> findAllActiveByUserId(@Param("userId") UUID userId);
}
