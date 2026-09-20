package com.agilify.core.watchers.repository;

import com.agilify.core.domain.entities.IssueWatcher;
import com.agilify.core.domain.entities.IssueWatcherId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WatcherRepository extends JpaRepository<IssueWatcher, IssueWatcherId> {

    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT w FROM IssueWatcher w WHERE w.id.issueId = :issueId ORDER BY w.addedAt")
    List<IssueWatcher> findByIssueId(@Param("issueId") UUID issueId);
}
