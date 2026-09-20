# Workflow Guard — how it works

## What problem it solves
An issue moves through statuses (`BACKLOG → TODO → IN_PROGRESS → IN_REVIEW → DONE`). Without a guard, anyone with DEVELOPER access could jump straight from BACKLOG to DONE, or reopen a DONE issue into IN_PROGRESS. The guard makes the project's **workflow** (which moves are legal, and who may make them) a rulebook stored in the database and enforced on every status change.

## The rulebook: table `workflow_config`
One row = one allowed move.

| column | meaning |
|---|---|
| `project_id` | rules are per project |
| `issue_type` | BUG / STORY / TASK / EPIC — each type can have its own flow |
| `from_state` → `to_state` | the allowed move |
| `required_role` | optional: ADMIN / DEVELOPER / VIEWER needed for this move (NULL = any DEVELOPER) |

`UNIQUE (project_id, issue_type, from_state, to_state)`. If a move has no row, it is illegal.

## Default rules (seeded)
`WorkflowSeeder.seedDefaults(project)` runs inside `ProjectService.createProject`, so every new project gets, for all 4 issue types:

```
BACKLOG ⇄ TODO
TODO    ⇄ IN_PROGRESS
IN_PROGRESS ⇄ IN_REVIEW
IN_REVIEW  → DONE
DONE       → TODO        (reopen)
```
That is 8 moves × 4 types = 32 rows per project. Anything else (BACKLOG→DONE, DONE→IN_PROGRESS, IN_PROGRESS→DONE …) is rejected.
Projects created before this feature have no rows; the guard seeds the defaults the first time it is asked.
To change a project's flow, edit its rows (or change `DEFAULT_TRANSITIONS` in `WorkflowSeeder` for new projects).

## Request flow
```
PATCH /api/v1/projects/{key}/issues/{id}/status   {"status": "in_progress"}
  │
  ├─ @PreAuthorize: caller must be DEVELOPER+ in the project   → else 403
  ├─ IssueService.updateIssueStatus
  │     newStatus = trim + UPPERCASE                            ("in_progress" → "IN_PROGRESS")
  │     WorkflowGuardService.validate(issue, newStatus, userId)
  │        1. same status as now?            → allowed, no-op
  │        2. project has no rules yet?      → seed defaults
  │        3. look up (project, issueType, from=current, to=new)
  │              not found                   → WorkflowTransitionException → HTTP 422 WORKFLOW_VIOLATION
  │        4. row has required_role?         → caller must hold that role, else 422
  │     record history row (field=status)    → see ISSUE_HISTORY.md
  │     set status, set/clear completed_at   (DONE sets it, leaving DONE clears it)
  └─ 200 with the updated issue
```

## Where the guard is applied
1. `PATCH .../issues/{id}/status`
2. `PATCH .../issues/{id}/position` when the body contains a `status` (dragging a card to another column)

Sprint rollover does not change status, so it does not go through the guard.

## Error response
```json
{ "status": 422, "error": "WORKFLOW_VIOLATION",
  "message": "Transition 'DONE' -> 'IN_PROGRESS' is not allowed for BUG issues in this project",
  "path": "/api/v1/projects/FOOD/issues/…/status", "timestamp": "…" }
```

## Good to know
- Statuses are plain text in `issues.status`; the workflow rows are the only thing defining valid values. An issue whose status is not in any rule cannot be moved until you add rows for it.
- Code: `issue/service/WorkflowGuardService.java`, `project/service/WorkflowSeeder.java`, `project/repository/WorkflowConfigRepository.java`, `common/exception/WorkflowTransitionException.java` (mapped in `GlobalExceptionHandler`).
- Test: `WorkflowGuardServiceTest` (unit) and `AgilifyApiFlowTest` (end to end).
