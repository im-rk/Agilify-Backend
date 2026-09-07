package com.agilify.core.project.mapper;

import com.agilify.core.domain.entities.Project;
import com.agilify.core.project.dto.ProjectResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProjectMapper {
    ProjectResponse toResponse(Project project);

    List<ProjectResponse> toResponseList(List<Project> projects);
}