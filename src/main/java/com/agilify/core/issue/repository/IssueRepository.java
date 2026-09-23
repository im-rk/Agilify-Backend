package com.agilify.core.issue.repository;

import com.agilify.core.domain.entities.Issue;
import com.agilify.core.domain.enums.IssuePriority;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssueRepository extends JpaRepository<Issue, UUID> {

    @Query("SELECT MAX(i.issueNumber) FROM Issue i WHERE i.project.id = :projectId")
    Optional<Integer> findMaxIssueNumberByProjectId(@Param("projectId") UUID projectId);

    @Query("SELECT i FROM Issue i JOIN FETCH i.project p LEFT JOIN FETCH i.assignee a LEFT JOIN FETCH i.reporter r WHERE p.key = :projectKey AND i.deletedAt IS NULL")
    List<Issue> findAllByProjectKeyWithDetails(@Param("projectKey") String projectKey);

    @Query("SELECT i FROM Issue i JOIN FETCH i.project p LEFT JOIN FETCH i.assignee LEFT JOIN FETCH i.reporter WHERE i.id = :id AND i.deletedAt IS NULL")
    Optional<Issue> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT COUNT(pm) > 0 FROM Issue i JOIN i.project p JOIN ProjectMember pm ON pm.project = p WHERE i.id = :issueId AND i.deletedAt IS NULL AND pm.user.id = :userId")
    boolean isUserMemberOfIssueProject(@Param("issueId") UUID issueId, @Param("userId") UUID userId);

    @Query("SELECT i FROM Issue i JOIN FETCH i.project p LEFT JOIN FETCH i.assignee LEFT JOIN FETCH i.reporter " +
            "WHERE p.key = :projectKey AND i.deletedAt IS NULL " +
            "AND (:status IS NULL OR i.status = :status) " +
            "AND (:priority IS NULL OR i.priority = :priority)")
    Page<Issue> findFilteredIssuesByProjectKey(
            @Param("projectKey") String projectKey,
            @Param("status") String status,
            @Param("priority") IssuePriority priority,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"assignee"})
    List<Issue> findByProjectKeyAndSprintIsNullAndDeletedAtIsNullOrderByBoardPositionAscCreatedAtAsc(String projectKey);

    @EntityGraph(attributePaths = {"assignee"})
    List<Issue> findBySprintIdAndDeletedAtIsNullOrderByBoardPositionAscCreatedAtAsc(UUID sprintId);

    // Find issues in a sprint where status is NOT equal to "DONE"
    List<Issue> findBySprintIdAndStatusNotAndDeletedAtIsNull(UUID sprintId, String status);
}
