package com.agilify.core.websocket;

import com.agilify.core.common.security.ProjectSecurityService;
import com.agilify.core.docs.service.DocService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

/**
 * Browsers can't set an Authorization header on a WebSocket handshake, so the JWT
 * comes in as a query param instead: /ws/docs/{docId}?token=...
 * Rejects the handshake unless the token is valid AND the caller has at least DEVELOPER
 * access to the doc's project — otherwise anyone with any valid token could open anyone's
 * doc, and a read-only VIEWER could push edits despite being unable to create/delete docs
 * through the REST API.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocWebSocketAuthInterceptor implements HandshakeInterceptor {

    public static final String ATTR_DOC_ID = "docId";
    public static final String ATTR_USER_ID = "userId";

    private final JwtDecoder jwtDecoder;
    private final DocService docService;
    private final ProjectSecurityService projectSecurity;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        UUID docId = extractDocId(request);
        String token = extractToken(request);
        if (docId == null || token == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            UUID userId = UUID.fromString(jwt.getSubject());

            String projectKey = docService.getProjectKeyForDoc(docId);
            if (projectKey == null || !projectSecurity.hasRole(projectKey, userId.toString(), "DEVELOPER")) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            attributes.put(ATTR_DOC_ID, docId);
            attributes.put(ATTR_USER_ID, userId);
            return true;
        } catch (Exception e) {
            log.warn("Rejected doc WebSocket handshake for doc {}: {}", docId, e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // nothing to do
    }

    private String extractToken(ServerHttpRequest request) {
        return UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
    }

    private UUID extractDocId(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        String idStr = path.substring(path.lastIndexOf('/') + 1);
        try {
            return UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
