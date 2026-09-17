package com.agilify.core.comments.mapper;

import com.agilify.core.comments.dto.CommentResponse;
import com.agilify.core.domain.entities.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CommentMapper {

    @Mapping(target = "issueId", source = "issue.id")
    @Mapping(target = "authorId", source = "author.id")
    @Mapping(target = "authorName", source = "author.fullName")
    @Mapping(target = "authorAvatarUrl", source = "author.avatarUrl")
    @Mapping(target = "parentCommentId", expression = "java(comment.getParentComment() != null ? comment.getParentComment().getId() : null)")
    CommentResponse toResponse(Comment comment);
}
