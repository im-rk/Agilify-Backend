package com.agilify.core.comments.repository;

import com.agilify.core.domain.entities.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    //fetch all active (not deleted) comments for a given issue, ordered by creation date ascending
    List<Comment> findByIssueIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID issueId);
}
