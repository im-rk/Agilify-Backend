package com.agilify.core.project.repository;

import com.agilify.core.domain.entities.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    
    @Query(value = "SELECT EXISTS(SELECT 1 FROM project_members WHERE project_id = :projectId AND user_id = :userId)", nativeQuery = true)
    boolean existsByProjectIdAndUserId(@Param("projectId") UUID projectId, @Param("userId") UUID userId);
    
    @Query(value = "SELECT * FROM project_members WHERE project_id = :projectId AND user_id = :userId", nativeQuery = true)
    Optional<ProjectMember> findByProjectIdAndUserId(@Param("projectId") UUID projectId, @Param("userId") UUID userId);
}
