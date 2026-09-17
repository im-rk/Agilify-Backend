package com.agilify.core.comments.security;

import com.agilify.core.comments.repository.CommentRepository;
import com.agilify.core.domain.enums.ProjectRole;
import com.agilify.core.issue.repository.IssueRepository;
import com.agilify.core.project.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("commentSecurity")
@RequiredArgsConstructor
public class CommentSecurityService {

    private final CommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final ProjectMemberRepository projectMemberRepository;

    /**
     * Checks if the user is a member of the project that owns the issue.
     */
    public boolean hasAccessByIssue(UUID issueId, UUID userId) {
        if (issueId == null || userId == null) {
            return false;
        }
        return issueRepository.isUserMemberOfIssueProject(issueId, userId);
    }

    /**
     * Checks if the user is the author of the comment or an ADMIN in the parent project.
     */
    public boolean isAuthorOrAdmin(UUID commentId, UUID userId) {
        if (commentId == null || userId == null) {
            return false;
        }

        return commentRepository.findById(commentId)
                .map(comment -> {
                    // Check if author
                    if (comment.getAuthor() != null && comment.getAuthor().getId().equals(userId)) {
                        return true;
                    }

                    // Check if admin of the project
                    if (comment.getIssue() != null && comment.getIssue().getProject() != null) {
                        UUID projectId = comment.getIssue().getProject().getId();
                        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                                .map(member -> member.getRole() == ProjectRole.ADMIN)
                                .orElse(false);
                    }

                    return false;
                })
                .orElse(false);
    }
}
