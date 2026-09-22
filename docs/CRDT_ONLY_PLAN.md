# Agilify + CRDT Docs — Trimmed Plan (Channels Dropped)

Answers the question first: **yes, this is fine.** Channels (`channels`, `channel_messages`, `message_reactions`, `channel_members`) and CRDT docs (`project_docs`, `doc_snapshots`) are two independent features glued only by the original week-by-week ordering in `docs/adv/07`, not by any real dependency. Nothing in the CRDT design (`docs/adv/03`) reads or writes a channel table, and nothing in the schema (`docs/adv/02`) makes one reference the other. You can delete channels from the plan and keep docs untouched.

What channels and docs *do* share is infrastructure: a WebSocket endpoint and (if you run more than one server instance) Redis pub-sub for fan-out. You still need that shared plumbing — you just build only as much of it as CRDT needs, not the channel-specific parts (session registry per channel, typing indicators, reactions, cursor pagination).

Presence and notifications (Phase 5) also don't depend on channels — they were only sequenced after channels in the original plan. They're **optional** for a CRDT-only goal: skip them unless you want to show "who else has this doc open."

---

## What you're keeping vs. dropping

| From `docs/adv/07` | Keep? | Why |
|---|---|---|
| Phase 1 (workflow, history, labels, watchers, board) | ✅ done | already shipped |
| Phase 2 — Redis + WebSocket infra | ⚠️ partial | keep WebSocket + doc tables; drop channel/presence-specific registries |
| Phase 3 — Channels + messaging | ❌ drop | not needed for CRDT |
| Phase 4 — CRDT docs | ✅ this is the goal | |
| Phase 5 — Presence + notifications | ⏭ optional, later | nice-to-have, not required to make docs collaborative |
| Phase 6 — Frontend | ✅ needed | a doc editor UI is required to see CRDT working at all |
| Phase 7 — JQL + deploy | ⏭ later, unrelated | |

---

## Revised build order

```
Step A   WebSocket support in Spring Boot (generic, no Redis yet)      ~0.5 day
Step B   Migration: project_docs + doc_snapshots only                  ~0.5 day
Step C   Doc metadata REST API (create/list/rename/delete)             ~0.5 day
Step D   CrdtDocWebSocketHandler — stateless relay, one process        ~1 day
Step E   Snapshot persistence (save/load from Postgres)                ~1 day
Step F   Frontend: Yjs + a WebSocket provider, two-tab test            ~1 day
Step G   (optional) Redis pub-sub, only if you'll run >1 server        ~0.5 day
```

A–F gets you a fully working collaborative doc on a single server instance. G is only needed the day you horizontally scale — skip it for now and revisit if/when it matters.

---

## Step A — WebSocket support (generic)

`pom.xml`: add `spring-boot-starter-websocket` only (leave out `spring-boot-starter-data-redis` until Step G).

`config/WebSocketConfig.java` registers one endpoint, `/ws/docs/{docId}`, and wires it to `CrdtDocWebSocketHandler`.

**Auth problem to solve now (same issue flagged for channels, still applies here):** browsers can't set an `Authorization` header on a WebSocket handshake. Options, pick one:
- pass the JWT as a query param (`/ws/docs/{docId}?token=...`) and validate it in a `HandshakeInterceptor` using the same `JwtDecoder` bean Spring Security already has;
- or send the token as the first WebSocket message and reject the connection if it doesn't arrive/validate in time.
Either way, also check the caller is a member of the doc's project (reuse `ProjectSecurityService`) before accepting the connection — otherwise anyone with a valid token could open anyone's doc.

Permit `/ws/**` in `SecurityConfig`'s `authorizeHttpRequests` (the handshake interceptor does the real auth check).

---

## Step B — Migration `V4__project_docs.sql`

(Numbered V4 because V2/V3 are already used by board-position and notifications — see `PROJECT_STATUS.md`.)

```sql
CREATE TABLE project_docs (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id  UUID        NOT NULL REFERENCES projects(id),
    title       VARCHAR(500) NOT NULL DEFAULT 'Untitled',
    created_by  UUID        NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_docs_project ON project_docs(project_id) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_docs_updated_at BEFORE UPDATE ON project_docs FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE doc_snapshots (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    doc_id      UUID        NOT NULL REFERENCES project_docs(id) ON DELETE CASCADE,
    snapshot    BYTEA       NOT NULL,   -- full Yjs document state, binary
    version     BIGINT      NOT NULL,   -- highest version wins on conflict
    saved_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_snapshots_doc_version ON doc_snapshots(doc_id, version DESC);
```

Only these 2 tables — not the 8-table `V3__squads_features.sql` from `docs/adv/02`. Total table count becomes 14 (11 original + notifications + board_position column + these 2), not 20.

JPA entities `ProjectDoc`, `DocSnapshot` following the pattern of existing entities (`Getter/Setter/Builder`, soft delete on `ProjectDoc` like `Issue`/`Comment`).

---

## Step C — Doc metadata REST API

```
POST   /api/v1/projects/{key}/docs            {title}         → 201, member can create (or require DEVELOPER — your call)
GET    /api/v1/projects/{key}/docs                             → 200, list (member)
PATCH  /api/v1/projects/{key}/docs/{id}/title  {title}         → 200
DELETE /api/v1/projects/{key}/docs/{id}                        → 204, soft delete
```
Same shape as every other controller/service/repository in this repo — `DocController`, `DocService`, `DocRepository`, `CreateDocRequest`/`DocResponse` records. No content lives here; content only exists as Yjs binary in `doc_snapshots`, relayed live over the WebSocket.

---

## Step D — `CrdtDocWebSocketHandler` (the core piece)

This is deliberately **stateless and dumb**: it never parses the Yjs binary, it just moves bytes.

```java
@Component
@RequiredArgsConstructor
public class CrdtDocWebSocketHandler extends BinaryWebSocketHandler {

    private final DocSessionRegistry sessions;      // Map<docId, Set<WebSocketSession>>
    private final DocSnapshotService snapshotService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID docId = docIdFrom(session);
        sessions.add(docId, session);
        // send the latest snapshot so the new client can reconstruct the doc
        snapshotService.loadLatest(docId).ifPresent(bytes ->
            sendBinary(session, bytes));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        UUID docId = docIdFrom(session);
        // broadcast to every OTHER session on this doc — no parsing, no understanding
        sessions.get(docId).stream()
            .filter(s -> !s.getId().equals(session.getId()))
            .forEach(s -> sendBinary(s, message.getPayload()));
        snapshotService.bufferUpdate(docId, message.getPayload()); // see Step E
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(docIdFrom(session), session);
    }
}
```

`DocSessionRegistry` is a plain `ConcurrentHashMap<UUID, Set<WebSocketSession>>` — no Redis needed for a single instance, because all sessions are already in this one process's memory.

---

## Step E — Snapshot persistence

- On each incoming binary update, buffer it (in memory, per doc) rather than writing to Postgres on every keystroke.
- Periodically (e.g. every 5–10s, or on last-disconnect from a doc) merge the buffered updates into the existing snapshot and save a new `doc_snapshots` row with `version = previous + 1`.
- On connect, `loadLatest(docId)` = the row with `MAX(version)` for that doc.
- Cleanup: keep only the latest snapshot per doc (delete older ones after a successful save), so the table doesn't grow unbounded.

This logic is pure Java/SQL — no Yjs-specific code runs on the server, because the server never decodes the binary. The actual CRDT merge logic (`Y.encodeStateAsUpdate`, `Y.applyUpdate`) all runs in the browser via the `yjs` npm package.

---

## Step F — Frontend integration test

In `Agilify-Frontend`: add `yjs` + a WebSocket provider compatible with a raw binary WebSocket (either write a ~50-line custom provider that speaks this handler's protocol, or adapt `y-websocket`'s protocol to match). Open the same doc in two browser tabs, type in one, confirm it appears in the other. Close both, reopen, confirm content is restored from the last snapshot.

---

## Step G — Redis (only when you actually scale out)

If you ever run more than one app instance behind a load balancer, in-memory `DocSessionRegistry` breaks — a user on server A won't see updates from a user on server B. At that point, add:
- `spring-boot-starter-data-redis`,
- publish every incoming update to Redis channel `doc:{docId}`,
- a subscriber on every instance that pushes received updates to its own local sessions.

This is the only genuinely inseparable piece of "channels infra" you'd otherwise have skipped — but it's generic pub-sub, not anything channel-specific, and it's a same-day add later. Don't build it now if you're running one instance for a college project/demo.

---

## What you're explicitly NOT building (from the dropped phases)

- `channels`, `channel_messages`, `message_reactions`, `channel_members` tables and all their endpoints/handlers.
- Typing indicators, emoji reactions, cursor-paginated message history.
- `user_presence` table, presence heartbeat, online/away/offline dots — unless you want it later purely for the doc editor ("who's viewing this doc"), which would be a much smaller slice than the full presence system in `docs/adv/05`.
- `notifications` read/mark-read endpoints — the table and write-path already exist from Phase 1/watchers; leave them as-is, you don't need to extend them for docs.

## Answer to your actual question
Yes — doing CRDT docs alone, skipping channels and messaging entirely, is a coherent and buildable slice. It doesn't leave anything broken or half-wired, because nothing in Phase 1 (already shipped) or the rest of the schema depends on channels existing. The only infra you still need to write yourself (Step A: WebSocket config + auth) is the same regardless of whether channels exist, so you're not losing any preparation work by dropping them.
