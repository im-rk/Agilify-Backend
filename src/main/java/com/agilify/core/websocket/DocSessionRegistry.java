package com.agilify.core.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory only — fine for one server instance. Move to Redis pub-sub once you run more than one. */
@Component
public class DocSessionRegistry {

    private final Map<UUID, Set<WebSocketSession>> sessionsByDoc = new ConcurrentHashMap<>();

    public void add(UUID docId, WebSocketSession session) {
        sessionsByDoc.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(UUID docId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByDoc.get(docId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByDoc.remove(docId);
        }
    }

    public Set<WebSocketSession> get(UUID docId) {
        return sessionsByDoc.getOrDefault(docId, Set.of());
    }
}
