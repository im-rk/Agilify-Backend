package com.agilify.core.sprints.mapper;

import com.agilify.core.domain.entities.Sprint;
import com.agilify.core.sprints.dto.SprintResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SprintMapper {

    @Mapping(target = "createdById", source = "createdBy.id")
    SprintResponse toResponse(Sprint sprint);

    List<SprintResponse> toResponseList(List<Sprint> sprints);
}
