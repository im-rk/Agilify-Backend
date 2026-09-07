package com.agilify.core.common.security;

import com.agilify.core.domain.enums.ProjectRole;
import com.agilify.core.project.repository.ProjectMemberRepository;
import com.agilify.core.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("projectSecurity ")
@RequiredArgsConstructor
public class ProjectSecurityService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public boolean hasRole(String projectKey, String userId, String requiredRoleName)
    {
        if(projectKey==null || userId==null || requiredRoleName==null)
        {
            return false;
        }

        ProjectRole requiredRole;
        try
        {
            requiredRole=ProjectRole.valueOf(requiredRoleName.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }

        return projectRepository.findByKey(projectKey.toUpperCase())
                .flatMap(project -> projectMemberRepository.findByProjectIdAndUserId(project.getId(), UUID.fromString(userId)))
                .map(member -> isAuthorized(member.getRole(), requiredRole))
                .orElse(false);
    }

    private boolean isAuthorized(ProjectRole actualRole,ProjectRole requiredRole)
    {
        if(actualRole==ProjectRole.ADMIN)
        {
            return true;
        }
        if(actualRole==ProjectRole.DEVELOPER)
        {
            return requiredRole==ProjectRole.DEVELOPER || requiredRole==ProjectRole.VIEWER;
        }
        if(actualRole==ProjectRole.VIEWER)
        {
            return requiredRole==ProjectRole.VIEWER;
        }
        return false;
    }

    public boolean isMember(String projectKey, String userId) {
        if (projectKey == null || userId == null) {
            return false;
        }

        // Convert the String userId from the JWT/Context into a UUID
        UUID userUuid = UUID.fromString(userId);

        // Call the new repository method
        return projectMemberRepository.existsByProjectKeyAndUserId(projectKey.toUpperCase(), userUuid);
    }
}
