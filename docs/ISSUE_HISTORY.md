# Issue History (audit log) — how it works

## What problem it solves
"Who changed this issue's status, and when?" Without history you only see the current state. Issue history is a permanent, **append-only** log: one row per changed field, never edited, never deleted.

## Storage: table `issue_history`
| column | meaning |
|---|---|
| `issue_id` | which issue |
| `changed_by` | user who made the change |
| `field_name` | `status`, `assignee_id`, `sprint_id`, `priority`, `title`, `description`, `issue_type`, `story_points`, `due_date` |
| `old_value` / `new_value` | text; ids are stored as UUID strings, null = "none" |
| `change_reason` | optional (sprint rollover writes "Sprint completed") |
| `changed_at` | set by the entity on insert |

## The one entry point
`IssueHistoryService.recordChange(issue, userId, fieldName, oldValue, newValue, reason)`

- If `old` equals `new` it does nothing — saving an unchanged field leaves no row.
- It runs in the **same transaction** as the change itself, so the issue update and its history row are saved together or not at all.
- After saving the row it calls `WatcherService.notifyWatchers(...)` (see Notifications below).
- The class only inserts and reads. There is no update or delete method anywhere.

## What triggers a history row
| Action | Field(s) recorded |
|---|---|
| `PATCH …/issues/{id}/status` | `status` |
| `PATCH …/issues/{id}/assignee` | `assignee_id` |
| `PUT …/issues/{id}` | each of `title`, `description`, `priority`, `issue_type`, `story_points`, `due_date` **that actually changed** |
| `PATCH …/issues/{id}/sprint` | `sprint_id` |
| `PATCH …/issues/{id}/position` | `sprint_id` and/or `status` if they changed |
| `POST …/sprints/{id}/complete` | `sprint_id` for every rolled-over issue (reason "Sprint completed") |

Not recorded: issue creation, deletion, label attach/detach.

## Reading it
`GET /api/v1/projects/{key}/issues/{id}/history` (any project member) → oldest first:
```json
[ { "fieldName": "status", "oldValue": "BACKLOG", "newValue": "TODO",
    "changedById": "…", "changedByName": "Ravi", "changedAt": "2026-09-20T…" } ]
```

## Notifications (the hook on top of history)
Every history row also fans out to the issue's **watchers**:
1. `WatcherService.notifyWatchers` loads the issue's watchers.
2. Skips the person who made the change.
3. Inserts one row per remaining watcher into `notifications` (`type` = `ISSUE_STATUS_CHANGED` / `ISSUE_ASSIGNED` / `ISSUE_UPDATED`, title `FOOD-3 updated`, body `status changed from TODO to IN_PROGRESS`).

Reporter and assignee are auto-watchers; anyone can `POST …/issues/{id}/watchers` to follow an issue.
`notifications` is stored only for now — endpoints to read/mark-read come in Phase 5 (real-time delivery too).

## Why `notifications` is a separate table
A history row describes *what happened to an issue* (one row, shared by everyone). A notification is *a message to one person about it* (one row per recipient, each with its own read/unread state, and it can also come from things that are not issue changes: mentions, sprint start, doc edits). Different lifecycle, different owner, so a separate table.

## Code
`issue/service/IssueHistoryService.java`, `issue/repository/IssueHistoryRepository.java`, `issue/dto/IssueHistoryResponse.java`, endpoint in `IssueController`; hook in `watchers/service/WatcherService.java`; `notifications/service/NotificationService.java`.
