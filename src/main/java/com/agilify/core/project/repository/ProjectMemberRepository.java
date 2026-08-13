package com.agilify.core.project.repository;

import com.agilify.core.domain.entities.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    
    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);
    
    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);
}
