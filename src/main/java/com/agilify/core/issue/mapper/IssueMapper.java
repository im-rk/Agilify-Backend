package com.agilify.core.issue.mapper;

import com.agilify.core.domain.entities.Issue;
import com.agilify.core.issue.dto.IssueResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface IssueMapper {

    @Mapping(target = "issueKey", expression = "java(issue.getProject().getKey() + \"-\" + issue.getIssueNumber())")
    @Mapping(source = "assignee.id", target = "assigneeId")
    @Mapping(source = "assignee.fullName", target = "assigneeName")
    @Mapping(source = "reporter.id", target = "reporterId")
    @Mapping(source = "reporter.fullName", target = "reporterName")
    IssueResponse toResponse(Issue issue);

    List<IssueResponse> toResponseList(List<Issue> issues);
}
