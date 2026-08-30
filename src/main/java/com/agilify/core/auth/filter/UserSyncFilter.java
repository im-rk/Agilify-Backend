package com.agilify.core.auth.filter;

import com.agilify.core.auth.repository.UserRepository;
import com.agilify.core.domain.entities.User;
import com.agilify.core.domain.enums.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserSyncFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var authentication= SecurityContextHolder.getContext().getAuthentication();

        if(authentication instanceof JwtAuthenticationToken jwtAuth) {
            UUID userId = UUID.fromString(jwtAuth.getToken().getSubject());
            if (!userRepository.existsById(userId)) {
                String email = jwtAuth.getToken().getClaimAsString("email");
                String fullName = jwtAuth.getToken().getClaimAsString("name");

                User newUser = new User();
                newUser.setId(userId);
                newUser.setEmail(email);
                newUser.setFullName(fullName != null ? fullName : email);
                newUser.setStatus(UserStatus.ACTIVE);
                newUser.setPasswordHash("MANAGED_BY_KEYCLOAK");

                userRepository.save(newUser);
            }
        }
        filterChain.doFilter(request, response);
    }
}
