package com.agilify.core.sprints.repository;

import com.agilify.core.domain.entities.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SprintRepository extends JpaRepository<Sprint, UUID> {
    @Query("SELECT s FROM Sprint s JOIN FETCH s.createdBy WHERE s.project.key = :projectKey")
    List<Sprint> findAllByProjectKeyWithCreator(@Param("projectKey") String projectKey);

    @Query("SELECT s FROM Sprint s JOIN FETCH s.createdBy WHERE s.id = :sprintId")
    Optional<Sprint> findByIdWithCreator(@Param("sprintId") UUID sprintId);

    Optional<Sprint> findFirstByProjectKeyAndStatus(String projectKey, com.agilify.core.domain.enums.SprintStatus status);

    boolean existsByProjectKeyAndStatus(String projectKey, com.agilify.core.domain.enums.SprintStatus status);
}
