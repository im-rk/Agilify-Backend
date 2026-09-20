package com.agilify.core.comments.service;

import com.agilify.core.auth.repository.UserRepository;
import com.agilify.core.comments.dto.CommentResponse;
import com.agilify.core.comments.dto.CreateCommentRequest;
import com.agilify.core.comments.dto.UpdateCommentRequest;
import com.agilify.core.comments.mapper.CommentMapper;
import com.agilify.core.comments.repository.CommentRepository;
import com.agilify.core.domain.entities.Comment;
import com.agilify.core.domain.entities.Issue;
import com.agilify.core.domain.entities.User;
import com.agilify.core.issue.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;

    @Transactional
    public CommentResponse addComment(UUID issueId, CreateCommentRequest request, UUID userId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found with id: " + issueId));

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        Comment parentComment = null;
        if (request.parentCommentId() != null) {
            parentComment = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent comment not found with ID: " + request.parentCommentId()));

            // Ensure parent belongs to the same issue
            if (!parentComment.getIssue().getId().equals(issueId)) {
                throw new IllegalArgumentException("Parent comment does not belong to this issue");
            }
        }

        Comment comment=Comment.builder()
                .issue(issue)
                .author(author)
                .parentComment(parentComment)
                .content(request.content().trim())
                .build();
        Comment savedComment=commentRepository.save(comment);
        return commentMapper.toResponse(savedComment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByIssue(UUID issueId) {
        if (!issueRepository.existsById(issueId)) {
            throw new IllegalArgumentException("Issue not found with ID: " + issueId);
        }

        List<Comment> comments = commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId);

        return comments.stream()
                .map(commentMapper::toResponse)
                .toList();
    }

    @Transactional
    public CommentResponse updateComment(UUID commentId, UpdateCommentRequest request, UUID userId) {
        // 1. Find the comment
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found with ID: " + commentId));

        if (comment.getDeletedAt() != null) {
            throw new IllegalArgumentException("Cannot update a deleted comment");
        }

        comment.setContent(request.content().trim());
        comment.setIsEdited(true);

        Comment updatedComment = commentRepository.save(comment);

        return commentMapper.toResponse(updatedComment);
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found with ID: " + commentId));

        if (comment.getDeletedAt() != null) {
            return; // Idempotent success or throw error
        }

        comment.setContent("[Comment deleted]");
        comment.setDeletedAt(java.time.Instant.now());
        commentRepository.save(comment);
    }
}
