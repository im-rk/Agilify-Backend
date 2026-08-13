package com.agilify.core.project.mapper;

import com.agilify.core.domain.entities.Project;
import com.agilify.core.project.dto.ProjectResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectMapper {
    ProjectResponse toResponse(Project project);
}
