package com.agilify.core.project.dto;

import com.agilify.core.domain.enums.ProjectStatus;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ProjectResponse {
    private UUID id;
    private String name;
    private String key;
    private String description;
    private String iconUrl;
    private ProjectStatus status;
    private Instant createdAt;
}
