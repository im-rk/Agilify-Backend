package com.agilify.core.project.dto;

import com.agilify.core.domain.enums.ProjectStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProjectRequest {
    @Size(max = 255)
    private String name;
    
    private String description;
    private String iconUrl;
    private ProjectStatus status;
}
