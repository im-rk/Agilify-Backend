package com.agilify.core.issue.repository;

import com.agilify.core.domain.entities.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssueRepository extends JpaRepository<Issue, UUID> {

    @Query("SELECT MAX(i.issueNumber) FROM Issue i WHERE i.project.id = :projectId")
    Optional<Integer> findMaxIssueNumberByProjectId(@Param("projectId") UUID projectId);
}
