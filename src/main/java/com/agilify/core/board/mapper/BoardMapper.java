package com.agilify.core.board.mapper;

import com.agilify.core.board.dto.IssueResponse;
import com.agilify.core.domain.entities.Issue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BoardMapper {

    @Mapping(target = "issueKey", expression = "java(projectKey.toUpperCase() + \"-\" + issue.getIssueNumber())")
    @Mapping(target = "priority", expression = "java(issue.getPriority() != null ? issue.getPriority().name() : null)")
    @Mapping(target = "issueType", expression = "java(issue.getIssueType() != null ? issue.getIssueType().name() : null)")
    @Mapping(target = "sprintId", source = "issue.sprint.id")
    @Mapping(target = "assigneeId", source = "issue.assignee.id")
    @Mapping(target = "assigneeName", source = "issue.assignee.fullName")
    IssueResponse toIssueResponse(Issue issue, String projectKey);
}
