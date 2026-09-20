package com.agilify.core.comments.repository;

import com.agilify.core.domain.entities.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    // includes soft-deleted comments (content tombstoned) so reply threads keep their parent
    List<Comment> findByIssueIdOrderByCreatedAtAsc(UUID issueId);
}
