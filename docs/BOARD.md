# Backlog, Sprints and the Board — how it works

## The idea (same as Jira)
Every issue lives in one of two places:
- **Backlog** — `sprint_id IS NULL`: work that is not planned into any sprint yet.
- **A sprint** — a time-boxed batch of work (`PLANNED → ACTIVE → CLOSED`). The **board** is the view of one sprint's issues, laid out in columns by `status` (TODO | IN_PROGRESS | IN_REVIEW | DONE) and ordered inside each column by `board_position`.

There is no "board" table. A board is a query: *issues of this sprint, not deleted, ordered by position*.

## Sprint lifecycle
```
create            POST  /projects/{key}/sprints                       → PLANNED
edit              PUT   /projects/{key}/sprints/{id}                  (only while PLANNED)
start             PATCH /projects/{key}/sprints/{id}/status  ACTIVE   (PLANNED → ACTIVE only;
                                                                       409 if another sprint is ACTIVE —
                                                                       also enforced by a DB unique index)
delete            DELETE /projects/{key}/sprints/{id}                 (only while PLANNED)
complete          POST  /projects/{key}/sprints/{id}/complete         (only from ACTIVE)
```
**Complete** sets `CLOSED` + `closedAt`, then finds every issue in the sprint whose status is not `DONE`:
- body `{"rolloverSprintId": "<id>"}` → they move to that sprint (must be same project, not CLOSED);
- no body → they go back to the backlog (`sprint_id = NULL`).
DONE issues stay attached to the closed sprint as its record. Each moved issue gets a `sprint_id` history row.

## Planning: moving issues between backlog and sprint
`PATCH /projects/{key}/issues/{issueId}/sprint` with `{"sprintId": "<id>"}` puts the issue into a sprint; `{"sprintId": null}` sends it back to the backlog.
Checks: issue belongs to the URL's project, is not deleted; sprint belongs to the same project and is not CLOSED.

## Reading the views
| Endpoint | Role | Returns |
|---|---|---|
| `GET /projects/{key}/backlog` | VIEWER | issues with no sprint, ordered by position then creation time |
| `GET /projects/{key}/sprints/{id}/board` | VIEWER | issues of that specific sprint (any status of sprint) |
| `GET /projects/{key}/board` | VIEWER | **the ACTIVE sprint + its issues** in one call; 404 if none is active |

### Why the "active board" endpoint exists
The screen users open every day is "what is the team working on right now". Without it the frontend has to list all sprints, find the one with `status=ACTIVE`, then request its issues — two calls and client-side logic. `GET /board` does it server-side in one call and returns `{ "sprint": {...}, "issues": [...] }`. The per-sprint endpoint is still there for looking at planned or closed sprints.

## Moving a card (drag and drop)
`PATCH /projects/{key}/issues/{issueId}/position` (DEVELOPER), body — all fields optional:
```json
{ "sprintId": "…", "status": "IN_REVIEW", "position": 3 }
```
- `status` → goes through the **workflow guard** (illegal move = 422), writes a history row, notifies watchers.
- `sprintId` → moves the card to that sprint (history row).
- `position` → stored in `issues.board_position` (index `idx_issues_board` on `(sprint_id, status, board_position)`).
Positions are stored exactly as sent; neighbouring cards are not renumbered (duplicates/gaps are possible — ordering falls back to creation time). If you want strict ordering, add a re-index step in `BoardService.updateIssueBoardPosition`.

## What an issue looks like in board responses
`id, issueKey, title, description, status, priority, issueType, storyPoints, dueDate, sprintId, boardPosition`.

## Code
`board/controller/BoardController`, `board/service/BoardService`, `board/mapper/BoardMapper`, `sprints/service/SprintService`, `sprints/controller/SprintController`; queries in `IssueRepository` (`…OrderByBoardPositionAscCreatedAtAsc`) and `SprintRepository`.
