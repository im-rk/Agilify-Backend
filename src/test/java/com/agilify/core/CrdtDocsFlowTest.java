package com.agilify.core;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real WebSocket connections over an actual TCP port, real Postgres persistence.
 * The only thing stubbed is JWT signature verification (TestJwtConfig below) —
 * Keycloak isn't running in this environment, so the token's subject is trusted
 * as-is instead of being cryptographically verified. Everything downstream
 * (handshake auth, membership check, relay, snapshot log) is the real code path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(CrdtDocsFlowTest.TestJwtConfig.class)
class CrdtDocsFlowTest {

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(token)
                    .claim("email", token + "@test.local")
                    .claim("name", "User " + token.substring(0, Math.min(4, token.length())))
                    .issuedAt(Instant.now().minusSeconds(5))
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;

    private RequestPostProcessor as(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("email", id + "@test.local").claim("name", "User"));
    }

    private String randomKey() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder("D");
        for (int i = 0; i < 6; i++) sb.append((char) ('A' + r.nextInt(26)));
        return sb.toString();
    }

    /** Captures binary frames a test client receives, so assertions can wait on them. */
    static class CapturingHandler extends BinaryWebSocketHandler {
        final BlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
        volatile CloseStatus closeStatus;

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            byte[] copy = new byte[message.getPayload().remaining()];
            message.getPayload().get(copy);
            received.add(copy);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            this.closeStatus = status;
        }

        byte[] poll() throws InterruptedException {
            return received.poll(5, TimeUnit.SECONDS);
        }
    }

    private WebSocketSession connect(UUID docId, UUID userId, CapturingHandler handler) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        return client.execute(handler, "ws://localhost:" + port + "/ws/docs/" + docId + "?token=" + userId)
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void docRestApi_rbacAndCrud() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        String key = randomKey();

        mvc.perform(get("/api/v1/test").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/test").with(as(viewer))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/test").with(as(outsider))).andExpect(status().isOk());

        mvc.perform(post("/api/v1/projects").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Docs\",\"key\":\"" + key + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/projects/" + key + "/members").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + viewer + "\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isCreated());

        // VIEWER cannot create a doc (create requires DEVELOPER)
        mvc.perform(post("/api/v1/projects/" + key + "/docs").with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Spec\"}"))
                .andExpect(status().isForbidden());
        // outsider (not a project member at all) is rejected too
        mvc.perform(get("/api/v1/projects/" + key + "/docs").with(as(outsider)))
                .andExpect(status().isForbidden());

        String created = mvc.perform(post("/api/v1/projects/" + key + "/docs").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Spec\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String docId = JsonPath.read(created, "$.id");
        assertEquals("Spec", JsonPath.read(created, "$.title"));

        // default title when none given
        String noTitle = mvc.perform(post("/api/v1/projects/" + key + "/docs").with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals("Untitled", JsonPath.read(noTitle, "$.title"));

        // VIEWER can read the list (member-only, not DEVELOPER-only)
        String list = mvc.perform(get("/api/v1/projects/" + key + "/docs").with(as(viewer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(2, ((java.util.List<?>) JsonPath.read(list, "$")).size());

        // single-doc lookup: member can fetch, outsider cannot
        mvc.perform(get("/api/v1/projects/" + key + "/docs/" + docId).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.title").value("Spec"));
        mvc.perform(get("/api/v1/projects/" + key + "/docs/" + docId).with(as(outsider)))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/projects/" + key + "/docs/" + docId + "/title").with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.title").value("Renamed"));

        mvc.perform(delete("/api/v1/projects/" + key + "/docs/" + docId).with(as(admin)))
                .andExpect(status().isNoContent());
        list = mvc.perform(get("/api/v1/projects/" + key + "/docs").with(as(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(1, ((java.util.List<?>) JsonPath.read(list, "$")).size()); // deleted doc no longer listed
    }

    @Test
    void websocketRelay_authAndLiveSync() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID dev = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        String key = randomKey();

        for (UUID u : new UUID[]{admin, dev, viewer, outsider}) {
            mvc.perform(get("/api/v1/test").with(as(u))).andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/projects").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Live\",\"key\":\"" + key + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/projects/" + key + "/members").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + dev + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/projects/" + key + "/members").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + viewer + "\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isCreated());

        String created = mvc.perform(post("/api/v1/projects/" + key + "/docs").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Live doc\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID docId = UUID.fromString(JsonPath.read(created, "$.id"));

        // 1. non-member is rejected at the handshake (never gets a WebSocketSession)
        CapturingHandler rejectedHandler = new CapturingHandler();
        StandardWebSocketClient client = new StandardWebSocketClient();
        ExecutionException rejected = assertThrows(ExecutionException.class, () ->
                client.execute(rejectedHandler, "ws://localhost:" + port + "/ws/docs/" + docId + "?token=" + outsider)
                        .get(5, TimeUnit.SECONDS));
        assertNotNull(rejected);

        // 1b. a VIEWER is a project member but not a DEVELOPER, so editing is still rejected
        CapturingHandler viewerHandler = new CapturingHandler();
        ExecutionException viewerRejected = assertThrows(ExecutionException.class, () ->
                client.execute(viewerHandler, "ws://localhost:" + port + "/ws/docs/" + docId + "?token=" + viewer)
                        .get(5, TimeUnit.SECONDS));
        assertNotNull(viewerRejected);

        // 2. two project members connect
        CapturingHandler adminSocket = new CapturingHandler();
        CapturingHandler devSocket = new CapturingHandler();
        WebSocketSession adminSession = connect(docId, admin, adminSocket);
        WebSocketSession devSession = connect(docId, dev, devSocket);
        assertTrue(adminSession.isOpen());
        assertTrue(devSession.isOpen());

        // 3. admin sends a binary "update" -> dev receives it live, admin does NOT receive its own echo
        byte[] update1 = {1, 2, 3, 4};
        adminSession.sendMessage(new BinaryMessage(ByteBuffer.wrap(update1)));
        byte[] receivedByDev = devSocket.poll();
        assertNotNull(receivedByDev, "dev should receive admin's update in real time");
        assertArrayEquals(update1, receivedByDev);
        assertNull(adminSocket.received.poll(500, TimeUnit.MILLISECONDS), "sender should not receive its own update back");

        // 4. it was persisted as an append-only update log row
        Integer rows = jdbc.queryForObject("select count(*) from doc_snapshots where doc_id = ?", Integer.class, docId);
        assertEquals(1, rows);

        // 5. dev sends one back -> admin receives it
        byte[] update2 = {9, 9};
        devSession.sendMessage(new BinaryMessage(ByteBuffer.wrap(update2)));
        assertArrayEquals(update2, adminSocket.poll());

        adminSession.close();
        devSession.close();

        // 6. a fresh connection gets the full history replayed in order, oldest first
        CapturingHandler lateJoiner = new CapturingHandler();
        WebSocketSession lateSession = connect(docId, admin, lateJoiner);
        assertArrayEquals(update1, lateJoiner.poll());
        assertArrayEquals(update2, lateJoiner.poll());
        lateSession.close();

        // 7a. a VIEWER must not be able to compact (an empty client state would wipe the doc)
        mvc.perform(post("/api/v1/projects/" + key + "/docs/" + docId + "/compact").with(as(viewer))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[]{0}))
                .andExpect(status().isForbidden());
        assertEquals(2, jdbc.queryForObject("select count(*) from doc_snapshots where doc_id = ?", Integer.class, docId));

        // 7. compacting collapses the log to a single row
        mvc.perform(post("/api/v1/projects/" + key + "/docs/" + docId + "/compact").with(as(admin))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[]{7, 7, 7}))
                .andExpect(status().isOk());
        rows = jdbc.queryForObject("select count(*) from doc_snapshots where doc_id = ?", Integer.class, docId);
        assertEquals(1, rows);
        Long version = jdbc.queryForObject("select version from doc_snapshots where doc_id = ?", Long.class, docId);
        assertEquals(1L, version);
    }
}
