package com.agilify.core;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the whole Jira-style flow against the real Postgres from docker-compose (port 5434). */
@SpringBootTest
@AutoConfigureMockMvc
class AgilifyApiFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final UUID admin = UUID.randomUUID();
    private final UUID dev = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();

    private RequestPostProcessor as(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString())
                .claim("email", id + "@test.local").claim("name", "User " + id.toString().substring(0, 4)));
    }

    private String body(MockHttpServletRequestBuilder req, UUID user, String json, int expected) throws Exception {
        if (json != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(req.with(as(user))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private String randomKey() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder("Z");
        for (int i = 0; i < 6; i++) sb.append((char) ('A' + r.nextInt(26)));
        return sb.toString();
    }

    @Test
    void fullFlow() throws Exception {
        // users get synced by UserSyncFilter on first request
        for (UUID u : new UUID[]{admin, dev, outsider}) {
            body(get("/api/v1/test"), u, null, 200);
        }

        // project (creator = ADMIN) + member
        String key = randomKey();
        body(post("/api/v1/projects"), admin, "{\"name\":\"Flow\",\"key\":\"" + key + "\"}", 201);
        body(post("/api/v1/projects/" + key + "/members"), admin, "{\"userId\":\"" + dev + "\",\"role\":\"DEVELOPER\"}", 201);
        body(post("/api/v1/projects/" + key + "/members"), admin, "{\"userId\":\"" + dev + "\",\"role\":\"DEVELOPER\"}", 409);
        body(post("/api/v1/projects/" + key + "/members"), admin, "{\"userId\":\"" + UUID.randomUUID() + "\",\"role\":\"VIEWER\"}", 404);
        assertEquals(32, jdbc.queryForObject("select count(*) from workflow_config where project_id = (select id from projects where key=?)", Integer.class, key));

        // RBAC
        body(get("/api/v1/projects/" + key + "/issues"), outsider, null, 403);
        body(get("/api/v1/projects/" + key), dev, null, 200);
        body(delete("/api/v1/projects/" + key + "/members/" + admin), admin, null, 409); // sole admin

        // issue: type/points mapped, key generated, assignee must be a member
        body(post("/api/v1/projects/" + key + "/issues"), dev,
                "{\"title\":\"x\",\"type\":\"TASK\",\"priority\":\"P2\",\"assigneeId\":\"" + outsider + "\"}", 400);
        String created = body(post("/api/v1/projects/" + key + "/issues"), dev,
                "{\"title\":\"Login bug\",\"type\":\"BUG\",\"priority\":\"P1\",\"storyPoints\":3,\"assigneeId\":\"" + dev + "\"}", 201);
        String issueId = JsonPath.read(created, "$.id");
        assertEquals(key + "-1", JsonPath.read(created, "$.issueKey"));
        assertEquals("BUG", JsonPath.read(created, "$.type"));
        assertEquals(3, (int) JsonPath.read(created, "$.storyPoints"));
        String base = "/api/v1/projects/" + key + "/issues/" + issueId;

        // watchers: admin watches, so admin is notified of dev's changes
        body(post(base + "/watchers"), admin, null, 200);
        String watchers = body(get(base + "/watchers"), dev, null, 200);
        assertEquals(2, ((java.util.List<?>) JsonPath.read(watchers, "$")).size()); // dev (auto) + admin

        // workflow guard
        body(patch(base + "/status"), dev, "{\"status\":\"DONE\"}", 422);
        body(patch(base + "/status"), dev, "{\"status\":\"todo\"}", 200);
        body(patch(base + "/status"), dev, "{\"status\":\"IN_PROGRESS\"}", 200);
        body(patch(base + "/status"), outsider, "{\"status\":\"TODO\"}", 403);

        // history + notification for the watcher
        String history = body(get(base + "/history"), admin, null, 200);
        assertEquals("status", JsonPath.read(history, "$[0].fieldName"));
        assertEquals("BACKLOG", JsonPath.read(history, "$[0].oldValue"));
        assertEquals("TODO", JsonPath.read(history, "$[0].newValue"));
        assertEquals("IN_PROGRESS", JsonPath.read(history, "$[1].newValue"));
        assertTrue(jdbc.queryForObject("select count(*) from notifications where recipient_id=?", Integer.class, admin) >= 2);
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where recipient_id=?", Integer.class, dev));

        // notifications read API: admin can list/read their own, but not another user's
        String notifications = body(get("/api/v1/notifications"), admin, null, 200);
        int notificationCount = ((java.util.List<?>) JsonPath.read(notifications, "$")).size();
        assertTrue(notificationCount >= 2);
        assertEquals(false, JsonPath.read(notifications, "$[0].isRead"));
        String firstNotificationId = JsonPath.read(notifications, "$[0].id");
        body(post("/api/v1/notifications/" + firstNotificationId + "/read"), outsider, null, 404); // not the recipient
        body(post("/api/v1/notifications/" + firstNotificationId + "/read"), admin, null, 200);
        String unread = body(get("/api/v1/notifications?unreadOnly=true"), admin, null, 200);
        assertEquals(notificationCount - 1, ((java.util.List<?>) JsonPath.read(unread, "$")).size());
        body(post("/api/v1/notifications/read-all"), admin, null, 200);
        unread = body(get("/api/v1/notifications?unreadOnly=true"), admin, null, 200);
        assertEquals(0, ((java.util.List<?>) JsonPath.read(unread, "$")).size());
        assertEquals(0, ((java.util.List<?>) JsonPath.read(body(get("/api/v1/notifications?unreadOnly=true"), dev, null, 200), "$")).size());

        // edit records history for changed fields only
        body(put(base), dev, "{\"title\":\"Login bug\",\"type\":\"BUG\",\"priority\":\"P3\",\"storyPoints\":5}", 200);
        history = body(get(base + "/history"), admin, null, 200);
        assertEquals(1, ((java.util.List<?>) JsonPath.read(history, "$[?(@.fieldName=='priority')]")).size());
        assertEquals(1, ((java.util.List<?>) JsonPath.read(history, "$[?(@.fieldName=='story_points')]")).size());
        assertEquals(0, ((java.util.List<?>) JsonPath.read(history, "$[?(@.fieldName=='title')]")).size());

        // labels
        String label = body(post("/api/v1/projects/" + key + "/labels"), dev, "{\"name\":\"backend\",\"colour\":\"#3B82F6\"}", 201);
        String labelId = JsonPath.read(label, "$.id");
        body(post("/api/v1/projects/" + key + "/labels"), dev, "{\"name\":\"BACKEND\"}", 409);
        body(post("/api/v1/projects/" + key + "/labels"), dev, "{\"name\":\"bad\",\"colour\":\"red\"}", 400);
        body(delete("/api/v1/projects/" + key + "/labels/" + labelId), dev, null, 403); // ADMIN only
        body(post(base + "/labels/" + labelId), dev, null, 200);
        body(post(base + "/labels/" + labelId), dev, null, 200); // idempotent
        assertEquals(1, ((java.util.List<?>) JsonPath.read(body(get(base + "/labels"), dev, null, 200), "$")).size());
        body(delete("/api/v1/projects/" + key + "/labels/" + labelId), admin, null, 204);
        assertEquals(0, ((java.util.List<?>) JsonPath.read(body(get(base + "/labels"), dev, null, 200), "$")).size());

        // sprint lifecycle + board
        String sprint = body(post("/api/v1/projects/" + key + "/sprints"), dev,
                "{\"name\":\"S1\",\"startDate\":\"" + LocalDate.now() + "\",\"endDate\":\"" + LocalDate.now().plusDays(14) + "\"}", 201);
        String sprintId = JsonPath.read(sprint, "$.id");
        body(get("/api/v1/projects/" + key + "/board"), dev, null, 404); // none active yet
        String backlog = body(get("/api/v1/projects/" + key + "/backlog"), dev, null, 200);
        assertEquals(1, ((java.util.List<?>) JsonPath.read(backlog, "$")).size());
        assertEquals(dev.toString(), JsonPath.read(backlog, "$[0].assigneeId"));
        body(patch("/api/v1/projects/" + key + "/issues/" + issueId + "/sprint"), dev, "{\"sprintId\":\"" + sprintId + "\"}", 200);
        assertEquals(0, ((java.util.List<?>) JsonPath.read(body(get("/api/v1/projects/" + key + "/backlog"), dev, null, 200), "$")).size());
        body(patch("/api/v1/projects/" + key + "/sprints/" + sprintId + "/status"), dev, "{\"status\":\"CLOSED\"}", 409);
        body(patch("/api/v1/projects/" + key + "/sprints/" + sprintId + "/status"), dev, "{\"status\":\"ACTIVE\"}", 200);
        String board = body(get("/api/v1/projects/" + key + "/board"), dev, null, 200);
        assertEquals(sprintId, JsonPath.read(board, "$.sprint.id"));
        assertEquals(1, ((java.util.List<?>) JsonPath.read(board, "$.issues")).size());
        assertEquals(dev.toString(), JsonPath.read(board, "$.issues[0].assigneeId"));
        assertEquals("User " + dev.toString().substring(0, 4), JsonPath.read(board, "$.issues[0].assigneeName"));
        String moved = body(patch("/api/v1/projects/" + key + "/issues/" + issueId + "/position"), dev, "{\"position\":3,\"status\":\"IN_REVIEW\"}", 200);
        assertEquals(3, (int) JsonPath.read(moved, "$.boardPosition"));
        assertEquals("IN_REVIEW", JsonPath.read(moved, "$.status"));
        body(patch("/api/v1/projects/" + key + "/issues/" + issueId + "/position"), dev, "{\"status\":\"TODO\"}", 422); // IN_REVIEW -> TODO not allowed

        // complete sprint: unfinished issue rolls back to backlog, closedAt set, history written
        String done = body(post("/api/v1/projects/" + key + "/sprints/" + sprintId + "/complete"), dev, null, 200);
        assertEquals("CLOSED", JsonPath.read(done, "$.status"));
        assertTrue(done.contains("closedAt\":\"") );
        assertEquals(1, ((java.util.List<?>) JsonPath.read(body(get("/api/v1/projects/" + key + "/backlog"), dev, null, 200), "$")).size());
        body(post("/api/v1/projects/" + key + "/sprints/" + sprintId + "/complete"), dev, null, 409); // already closed

        // comments: soft delete keeps thread, admin can edit others' comments
        String c1 = body(post("/api/v1/issues/" + issueId + "/comments"), dev, "{\"content\":\"root\"}", 201);
        assertEquals(false, JsonPath.read(c1, "$.isEdited"));
        String cid = JsonPath.read(c1, "$.id");
        body(post("/api/v1/issues/" + issueId + "/comments"), dev, "{\"content\":\"reply\",\"parentCommentId\":\"" + cid + "\"}", 201);
        body(put("/api/v1/issues/" + issueId + "/comments/" + cid), admin, "{\"content\":\"edited by admin\"}", 200);
        body(put("/api/v1/issues/" + issueId + "/comments/" + cid), outsider, "{\"content\":\"nope\"}", 403);
        body(delete("/api/v1/issues/" + issueId + "/comments/" + cid), dev, null, 204);
        String comments = body(get("/api/v1/issues/" + issueId + "/comments"), admin, null, 200);
        assertEquals(2, ((java.util.List<?>) JsonPath.read(comments, "$")).size());
        assertEquals("[Comment deleted]", JsonPath.read(comments, "$[0].content"));

        // soft-deleted issue disappears
        body(delete(base), dev, null, 204);
        body(get(base), dev, null, 404);
        assertEquals(0, ((java.util.List<?>) JsonPath.read(body(get("/api/v1/projects/" + key + "/backlog"), dev, null, 200), "$")).size());
    }
}
