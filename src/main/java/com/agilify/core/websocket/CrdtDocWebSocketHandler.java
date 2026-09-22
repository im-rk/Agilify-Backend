package com.agilify.core.websocket;

import com.agilify.core.docs.service.DocSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.util.UUID;

/**
 * Stateless relay: never parses the Yjs binary, just archives it and forwards it to
 * every other session on the same doc. All the actual CRDT merge logic runs client-side.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrdtDocWebSocketHandler extends BinaryWebSocketHandler {

    private final DocSessionRegistry sessions;
    private final DocSnapshotService snapshotService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID docId = docId(session);
        sessions.add(docId, session);

        for (byte[] update : snapshotService.loadAllUpdates(docId)) {
            session.sendMessage(new BinaryMessage(update));
        }
        log.info("doc {} : session {} connected ({} total)", docId, session.getId(), sessions.get(docId).size());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        UUID docId = docId(session);
        byte[] payload = message.getPayload().array();

        snapshotService.appendUpdate(docId, payload);

        for (WebSocketSession peer : sessions.get(docId)) {
            if (!peer.getId().equals(session.getId()) && peer.isOpen()) {
                peer.sendMessage(new BinaryMessage(payload));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID docId = docId(session);
        sessions.remove(docId, session);
        log.info("doc {} : session {} disconnected ({})", docId, session.getId(), status);
    }

    private UUID docId(WebSocketSession session) {
        return (UUID) session.getAttributes().get(DocWebSocketAuthInterceptor.ATTR_DOC_ID);
    }
}
