# Agilify Core — Implementation Plan (What To Build Next, In What Order)

Date: 2026-09-19. Built from: the code in `core/src`, `PROJECT_STATUS.md`, and the Squads plan in `docs/adv` (files 01–07).

**Where you are:** Jira-style core is done (Projects, Issues, Sprints, Board, Comments, RBAC). The next goal is the **Squads integration** = finish 4 half-built features, then add real-time channels, CRDT collaborative docs, presence and notifications, then JQL, frontend, deploy.

**Rule from the plan:** each phase has a verification test. Do not start the next phase until the current one passes.

---

## Order at a glance

```
Phase 0  Make the existing app run + first integration test     (0.5–1 day)   ← START HERE
Phase 1  Finish half-built features (5 items)                   (Week 1)
Phase 2  Infrastructure: Redis + WebSocket + V2/V3 migrations   (Week 2)
Phase 3  Real-time channels + messaging                         (Week 3)
Phase 4  CRDT collaborative docs (Yjs)                          (Week 4)
Phase 5  Presence + notifications                               (Week 5)
Phase 6  Frontend (Agilify-Frontend)                            (Week 6)
Phase 7  JQL engine + polish + deploy                           (Week 7)
Later    Analytics, Elasticsearch, time logs, issue links       (optional)
```

Why this order: Phase 1 items feed Phase 5 (history → notifications, watchers → notifications). Phase 2 is the foundation for 3, 4, 5. CRDT (Phase 4) reuses the WebSocket + Redis pattern proven in Phase 3, so channels come first even though CRDT is the "highest priority" feature.

---

## Phase 0 — Make it run (do this before anything else)

Nothing in the project has been executed yet (no DB/Keycloak run). Every later phase assumes a working base.

1. `docker compose up -d db redis keycloak` (Keycloak is now on host port **8180**, Postgres **5434**, Redis **6381**).
2. In Keycloak: create the realm, a public client, 2 test users. Set env `KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/<your-realm>` (default in `application.properties` assumes realm `agilify`).
3. Start the app. Flyway `V1` must apply and `ddl-auto=validate` must pass. Fix any entity↔column mismatch.
4. Smoke test in Swagger with a real token: create project → add member → create issue → create sprint → start → assign issue → complete → comment → a forbidden call (expect clean 403).
5. Write one Testcontainers integration test of that flow.

**Done when:** app boots, the smoke path passes, the integration test is green.

---

## Phase 1 — Finish the half-built features (Week 1)

Adapt the plan's snippets to the real code: the plan says `feature/...` packages and `authentication.name`; this repo uses `com.agilify.core.<module>` and `#userId.toString()` with `@CurrentUser UUID userId`. Keep the repo's style.

### 1.1 Workflow seeding + Workflow Guard (Day 1–2)
- **Prerequisite the docs assume but don't exist:** a `WorkflowSeeder`. Seed default transitions per issue type (BACKLOG→TODO→IN_PROGRESS→IN_REVIEW→DONE, plus back-transitions) inside `ProjectService.createProject`.
- Add `existsByProjectIdAndIssueTypeAndFromStateAndToState` to `WorkflowConfigRepository`.
- New `WorkflowGuardService.validate(...)`, called from `IssueService.updateIssueStatus`; honour `required_role`.
- New `WorkflowTransitionException` → **422** `WORKFLOW_VIOLATION` in `GlobalExceptionHandler`.
- **Verify:** DONE→IN_PROGRESS (not in config) → 422; TODO→IN_PROGRESS → 200.

### 1.2 Issue History (Day 2–3)
- `IssueHistoryRepository`, `IssueHistoryService.recordChange(issueId, userId, field, old, new, reason)` — **append-only**, never update/delete.
- Call it from: status, assignee, sprint (incl. sprint completion rollover), priority, title/description edits.
- `GET /api/v1/projects/{key}/issues/{id}/history` (member).
- **Verify:** change status → history endpoint shows `field=status, old→new`.

### 1.3 Labels API (Day 3–4)
- `POST/GET/DELETE /api/v1/projects/{key}/labels` (create=DEVELOPER, list=member, delete=ADMIN).
- `POST/DELETE/GET /api/v1/projects/{key}/issues/{id}/labels/{labelId}`. Enforce label and issue belong to the same project.
- **Verify:** create label → attach → list on issue.

### 1.4 Issue Watchers + notification hook (Day 4–5)
- `POST/DELETE/GET .../issues/{id}/watchers` (member). Auto-watch reporter and assignee.
- `IssueWatcherService.notifyWatchers(...)` is called from `IssueHistoryService`; it needs the `notifications` table, so create it now (see 2.2) or stub the call until Phase 5.

### 1.5 Board position (Day 5)
- Flyway `V2__add_board_position.sql`: `issues.board_position INTEGER` + index `idx_issues_board`.
- Route for the existing dead code: `PATCH /api/v1/projects/{key}/issues/{id}/position`; route it through the workflow guard. Add `GET /api/v1/projects/{key}/board` (active sprint) and expose `storyPoints`, `dueDate`, `sprintId`, `boardPosition` in the issue response.

**Phase 1 done when:** the Week-1 checklist in `docs/adv/07` passes (history writes, 422 on bad transition, labels, watchers, board position).

**Fold in hardening while touching these files:** issue-number race (lock the project row or retry), assignee must be a project member, `addMember` must check the user exists, comment tombstone (`[Comment deleted]`) instead of hiding replies, clear `completed_at` when leaving DONE.

---

## Phase 2 — Infrastructure (Week 2)

### 2.1 Redis + WebSocket setup
- `pom.xml`: `spring-boot-starter-data-redis`, `spring-boot-starter-websocket` (and remove unused `jjwt-*` + `jwt.secret`).
- `application.properties`: Redis host/port (compose maps Redis to **6381**), `websocket.allowed-origins` (frontend origin).
- `config/RedisConfig.java` (listener container, `StringRedisTemplate`), `config/WebSocketConfig.java`.
- **WebSocket auth (not covered in the docs — decide now):** browsers can't set an `Authorization` header on a WebSocket, so pass the JWT as a query param / first message and validate it in a `HandshakeInterceptor` with the same `JwtDecoder`. Also permit `/ws/**` in `SecurityConfig` and check project membership in the handler.

### 2.2 Flyway `V3__squads_features.sql` — 8 new tables (final total = 20)
`channels`, `channel_messages`, `message_reactions`, `channel_members`, `project_docs`, `doc_snapshots` (bytea), `user_presence`, `notifications`. SQL is fully written in `docs/adv/02`. Add JPA entities + repositories; `ddl-auto=validate` will catch any mismatch.
New enums: `PresenceStatus`, `NotificationType`, `ChannelMessageType`.

### 2.3 Session registries, handler stubs, Redis subscribers
`ChannelSessionRegistry`, `DocSessionRegistry`, `PresenceSessionRegistry`; stub handlers for `/ws/channels/{id}`, `/ws/docs/{id}`, `/ws/presence/{projectId}`; subscribers for `channel:*`, `doc:*`, presence.

**Verify:** `redis-cli ping` → PONG; `wscat` connects to each `/ws/...`; `redis-cli publish channel:test hello` → app logs it.

---

## Phase 3 — Channels + messaging (Week 3)

- REST: `POST/GET /api/v1/projects/{key}/channels`; seed a `#general` channel on project creation.
- Messages: store in `channel_messages`; cursor pagination `?before=<cursor>&limit=50` using index `(channel_id, created_at DESC, id DESC)`; threads via `parent_message_id`.
- `ChannelWebSocketHandler`: receive → persist → publish to Redis `channel:{id}` → every server's subscriber pushes to its local sessions (this is the fan-out).
- Typing indicators (Redis set `typing:{channelId}`, TTL 5s); reactions via REST (`message_reactions`, unique per user+emoji); private channels via `channel_members`.

**Verify:** tab 1 sends → tab 2 receives instantly; older-message pagination works; reactions add/remove.

---

## Phase 4 — CRDT collaborative docs (Week 4)

- REST: `POST/GET /api/v1/projects/{key}/docs`, `PATCH .../docs/{id}/title`, delete (soft).
- `CrdtDocWebSocketHandler`: **stateless relay** — receive binary Yjs update, broadcast to other sessions in the doc and via Redis `doc:{id}`; never parse content.
- `DocSnapshotService`: on first connect send the latest snapshot; periodically (and on last-disconnect) merge into a new snapshot with a monotonically increasing `version` (highest version wins on conflict); cleanup job keeps only the latest.
- Frontend uses `yjs` + a y-websocket-compatible provider (Day 5 compatibility test in the plan).

**Verify:** edit in tab 1 appears in tab 2; disconnect both, reconnect → content restored from `doc_snapshots`.

---

## Phase 5 — Presence + notifications (Week 5)

- Presence: WebSocket heartbeat every ~15s sets Redis key `presence:{userId}:{projectId}` with **TTL 30s**; upsert `user_presence.last_seen_at` for "last seen". `GET /api/v1/projects/{key}/presence` → ONLINE/AWAY/OFFLINE per member.
- Notifications: `GET /api/v1/notifications`, `POST /notifications/{id}/read`, `POST /notifications/read-all`. Create them from: watcher notifications (Phase 1.4), `@mention` in channel messages, sprint start/close, doc edits. Push over WebSocket and keep REST for history.
- Integration test (plan Day 5): create project → #general seeded → two users online → message delivered live → doc edited live → watcher notified → close browser → OFFLINE after 30s.

---

## Phase 6 — Frontend (Week 6)
`Agilify-Frontend` (React/Vite, currently a bare scaffold): Keycloak login, projects/issues/board/backlog/sprints, then channel sidebar + live messages, collaborative editor, presence dots, notification bell.

---

## Phase 7 — JQL + polish + deploy (Week 7)
- JQL: `JqlTokenizer` → `JqlParser` (recursive descent → sealed AST) → `JqlSqlGenerator` (parameterised SQL, never string-concatenated). `POST /api/v1/projects/{key}/issues/search` with `{"jql": "assignee = me AND priority = P1 AND sprint = active"}`.
- Deploy: add `app` service to docker-compose, EC2 t2.micro with Redis `maxmemory 200mb`, env vars for datasource/Redis/Keycloak issuer.
- Full manual smoke test of everything.

## Later (from `docs/general`, not in the Squads plan)
Sprint analytics (velocity / burndown / cycle time via window functions), Elasticsearch full-text + transactional outbox, time logs, custom field definitions, issue links.

---

## Decisions/risks to settle early
1. **WebSocket auth scheme** (Phase 2.1) — not specified in the docs; affects all three handlers.
2. **Keycloak realm/client names** — set once, used by backend issuer URI and frontend.
3. **Issue status as free text** — the workflow guard makes this safe enough; add a DB `CHECK` or enum if you want stricter data.
4. **Docs vs code naming** — docs use `feature/*` packages and `authentication.name`; keep this repo's `com.agilify.core.<module>` layout and `@CurrentUser UUID`.
5. **Tests** — add integration tests per phase; there are none today.

## Audit fixes already applied (for reference)
Beyond the first 9 bug fixes: removed a broken derived query that would crash startup, added the missing JWT `issuer-uri`, moved Keycloak off the app's port, made all issue queries respect soft-delete, blocked cross-project sprint assignment, fixed the sprint lifecycle (`closedAt`, ACTIVE-only completion, PLANNED→ACTIVE only), mapped IllegalArgument/IllegalState to 400/409, VIEWER can read the sprint board, statuses are upper-cased.
