# Agilify — Backend Project Status

Snapshot of the `core` Spring Boot backend as of 2026-09-18. Covers what's built, every API endpoint, every DB table, and every enum. Frontend (`Agilify-Frontend`) is not covered here.

Stack: Spring Boot 3 / Java 17, Spring Data JPA + PostgreSQL (Flyway migration), Spring Security (JWT resource server, Keycloak), MapStruct, Lombok.

---

## 1. Modules built

| Module | Package | Status |
|---|---|---|
| Identity & Security | `auth`, `common.security` | ✅ Done |
| Projects & Membership | `project` | ✅ Done |
| Issues | `issue` | ✅ Done |
| Sprints | `sprints` | ✅ Done |
| Board & Backlog | `board` | ✅ Done |
| Comments (threaded) | `comments` | ✅ Done |
| Issue History (audit log) | `domain.entities.IssueHistory` | ⚠️ Table + entity only, nothing writes to it |
| Workflow Guard | `domain.entities.WorkflowConfig` | ⚠️ Table + entity + repository only, no service enforces transitions |
| Labels | `domain.entities.Label`, `IssueLabel` | ⚠️ Table + entity only, no API |
| Issue Watchers | `domain.entities.IssueWatcher` | ⚠️ Table + entity only, no API |

Everything marked ✅ has controller + service + DTOs + repository wired end-to-end and compiles clean. Everything marked ⚠️ has a DB table and JPA entity from the schema design but no business logic or endpoints yet — these are the real gaps, not bugs.

---

## 2. API Endpoints (all currently live)

### Projects — `/api/v1/projects`
| Method | Path | Role required | Description |
|---|---|---|---|
| POST | `/` | any authenticated user | Create project (creator becomes ADMIN) |
| GET | `/` | any authenticated user | List projects the caller is a member of |
| GET | `/{key}` | member | Get project by key |
| PUT | `/{key}` | ADMIN | Update project details |
| POST | `/{key}/members` | ADMIN | Add a member with a role |
| DELETE | `/{key}/members/{memberId}` | ADMIN | Remove a member (blocked if they're the sole ADMIN) |

### Issues — `/api/v1/projects/{key}/issues`
| Method | Path | Role required | Description |
|---|---|---|---|
| POST | `/` | DEVELOPER | Create issue |
| GET | `/` | member | Paginated + filtered issue list (status, priority) |
| GET | `/{id}` | member | Get issue by ID |
| PATCH | `/{id}/status` | DEVELOPER | Update status (sets `completedAt` on DONE) |
| PATCH | `/{id}/assignee` | DEVELOPER | Assign/unassign issue |
| PUT | `/{id}` | DEVELOPER | Update title/description/priority/type |
| DELETE | `/{id}` | DEVELOPER | Soft-delete issue |

### Sprints — `/api/v1/projects/{key}/sprints`
| Method | Path | Role required | Description |
|---|---|---|---|
| POST | `/` | DEVELOPER | Create sprint (status = PLANNED) |
| GET | `/` | member | List project sprints |
| GET | `/{sprintId}` | member | Get sprint by ID |
| PUT | `/{sprintId}` | DEVELOPER | Update sprint (only while PLANNED) |
| PATCH | `/{sprintId}/status` | DEVELOPER | Transition status (enforces only one ACTIVE sprint per project) |
| DELETE | `/{sprintId}` | DEVELOPER | Delete sprint (only while PLANNED) |
| POST | `/{sprintId}/complete` | DEVELOPER | Close sprint; rolls unfinished issues to a target sprint or the backlog |

### Board & Backlog — `/api/v1/projects/{key}`
| Method | Path | Role required | Description |
|---|---|---|---|
| PATCH | `/issues/{issueId}/sprint` | DEVELOPER | Move an issue into/out of a sprint |
| GET | `/backlog` | member (VIEWER) | Issues with no sprint assigned |
| GET | `/sprints/{sprintId}/board` | DEVELOPER | Issues on a given sprint's board |

> `BoardService.updateIssueBoardPosition` exists but is **not wired to any controller route** — dead code today, presumably meant for drag-and-drop status/position updates.

### Comments — `/api/v1/issues/{issueId}/comments`
| Method | Path | Role required | Description |
|---|---|---|---|
| POST | `/` | project member (of the issue's project) | Add root comment or threaded reply |
| GET | `/` | project member | List non-deleted comments, chronological |
| PUT | `/{commentId}` | author or project ADMIN | Edit comment, sets `isEdited` |
| DELETE | `/{commentId}` | author or project ADMIN | Soft-delete comment |

### Misc
- `GET /api/v1/test` — health/sanity check controller, no auth.

---

## 3. Database tables (Flyway `V1__initial_schema.sql`, 11 tables)

| # | Table | Purpose | Notable constraints |
|---|---|---|---|
| 1 | `users` | User accounts (synced from Keycloak JWT on first request) | unique active email |
| 2 | `projects` | Projects | key format `^[A-Z]{2,10}$`, unique active key |
| 3 | `project_members` | User↔Project membership + role | unique (project_id, user_id) |
| 4 | `workflow_config` | Per-project allowed status transitions per issue type | unique (project_id, issue_type, from_state, to_state) — **not enforced yet** |
| 5 | `sprints` | Sprints | only one `ACTIVE` sprint per project (partial unique index) |
| 6 | `issues` | Issues | unique (project_id, issue_number); full-text + JSONB GIN indexes; trigger auto-sets `started_at`/`completed_at` on status change |
| 7 | `issue_history` | Audit trail of field changes | **nothing writes to this table yet** |
| 8 | `labels` | Project-scoped labels | unique (project_id, name) — **no API yet** |
| 9 | `issue_labels` | Issue↔Label join | — **no API yet** |
| 10 | `issue_watchers` | Issue↔User "watching" join | — **no API yet** |
| 11 | `comments` | Threaded comments on issues | self-referencing `parent_comment_id`, soft delete |

All tables have `updated_at` auto-maintained via a shared Postgres trigger (`set_updated_at`).

---

## 4. Enums

| Enum | Values | Used by |
|---|---|---|
| `ProjectRole` | `ADMIN`, `DEVELOPER`, `VIEWER` | `project_members.role`, RBAC checks in `ProjectSecurityService` |
| `ProjectStatus` | `ACTIVE`, `ARCHIVED` | `projects.status` |
| `IssueType` | `BUG`, `STORY`, `TASK`, `EPIC` | `issues.issue_type`, `workflow_config.issue_type` |
| `IssuePriority` | `P1`, `P2`, `P3`, `P4` | `issues.priority` |
| `SprintStatus` | `PLANNED`, `ACTIVE`, `CLOSED` | `sprints.status` |
| `UserStatus` | `ACTIVE`, `SUSPENDED` | `users.status` |

Note: `issues.status` (BACKLOG/TODO/IN_PROGRESS/DONE/etc.) is a **free-text `VARCHAR`, not a Java enum** — validated only by a DB `CHECK` in spirit but actually not constrained at the DB level either. This is why a workflow guard against `workflow_config` doesn't exist yet — there's no enum to validate against.

---

## 5. Security model

- JWT bearer auth via Spring Security OAuth2 resource server (Keycloak-issued tokens).
- `UserSyncFilter` auto-creates a local `users` row the first time a new JWT subject is seen.
- `@CurrentUser` argument resolver injects the JWT subject as a `UUID` into controller methods.
- `ProjectSecurityService` (bean `projectSecurity`) — `hasRole(key, userId, role)` (ADMIN > DEVELOPER > VIEWER hierarchy) and `isMember(key, userId)`, used in `@PreAuthorize` on nearly every endpoint.
- `CommentSecurityService` (bean `commentSecurity`) — `hasAccessByIssue` (project membership via the issue) and `isAuthorOrAdmin` (comment author or project ADMIN).

---

## 6. Recent bug-fix pass (this session)

A full audit of every module surfaced and fixed 9 real defects — most importantly a typo'd Spring bean name (`"projectSecurity "` with a trailing space) that silently broke **every** `@PreAuthorize` check in the app, plus a wrong exception class that meant authorization failures weren't returning clean 403s. Full list and rationale covered in the prior conversation turn; nothing further to redo there. The project now builds clean with `mvn clean compile` (zero warnings).

---

## 7. What's left (real gaps, not bugs)

1. **Workflow Guard** — validate issue status transitions against `workflow_config` before allowing `PATCH /issues/{id}/status`.
2. **Issue History audit logging** — write a row to `issue_history` whenever status/assignee/sprint changes.
3. **Labels API** — CRUD for `labels` + attach/detach on `issue_labels`.
4. **Issue Watchers API** — subscribe/unsubscribe, notify list.
5. **Board position endpoint** — wire the existing `BoardService.updateIssueBoardPosition` to a route, or delete it if unneeded.
