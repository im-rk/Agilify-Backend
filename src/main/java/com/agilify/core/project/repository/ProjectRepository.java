package com.agilify.core.project.repository;

import com.agilify.core.domain.entities.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    
    boolean existsByKey(String key);

    Optional<Project> findByKeyAndDeletedAtIsNull(String key);

    // Fetch all active projects where the user is a member
    @Query("""
        SELECT p FROM Project p 
        JOIN ProjectMember pm ON p.id = pm.project.id 
        WHERE pm.user.id = :userId AND p.deletedAt IS NULL
    """)
    List<Project> findAllActiveByUserId(@Param("userId") UUID userId);
}
