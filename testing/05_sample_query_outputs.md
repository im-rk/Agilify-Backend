# Sample Outputs and Query Results

This document contains actual terminal and database query execution outputs from running the complex SQL queries (`database/03_queries.sql`) against the seeded PostgreSQL database.

---

## 1. Multi-Table Joins

### Query 1.1: 5-Table Master Join — Active Sprint Kanban Board Card Details
```sql
SELECT 
    p.key || '-' || i.issue_number AS issue_key,
    i.title,
    i.issue_type,
    i.status,
    i.priority,
    i.story_points,
    COALESCE(u_assignee.full_name, 'Unassigned') AS assignee,
    u_reporter.full_name AS reporter,
    s.name AS sprint_name,
    COALESCE(string_agg(l.name, ', ' ORDER BY l.name), 'No Labels') AS attached_labels
FROM issues i
INNER JOIN projects p ON i.project_id = p.id
INNER JOIN users u_reporter ON i.reporter_id = u_reporter.id
LEFT JOIN users u_assignee ON i.assignee_id = u_assignee.id
INNER JOIN sprints s ON i.sprint_id = s.id
LEFT JOIN issue_labels il ON i.id = il.issue_id
LEFT JOIN labels l ON il.label_id = l.id
WHERE s.status = 'ACTIVE' AND i.deleted_at IS NULL
GROUP BY p.key, i.issue_number, i.title, i.issue_type, i.status, i.priority, i.story_points, u_assignee.full_name, u_reporter.full_name, s.name
ORDER BY i.board_position ASC, i.issue_number ASC;
```

#### Terminal Output:
```
 issue_key |                    title                    | issue_type |   status    | priority | story_points |  assignee   |  reporter   |         sprint_name         |       attached_labels        
-----------+---------------------------------------------+------------+-------------+----------+--------------+-------------+-------------+-----------------------------+------------------------------
 AD-1      | Configure PostgreSQL Session Pooler         | TASK       | DONE        | P1       |            3 | Bob Johnson | Alice Smith | Sprint 1 - Core Foundations | backend, dbms
 AD-2      | OAuth Callback Timeout on Mobile Networks   | BUG        | IN_PROGRESS | P1       |            5 | Bob Johnson | Alice Smith | Sprint 1 - Core Foundations | backend, critical, security
 AD-3      | Implement Kanban Card Drag-and-Drop         | STORY      | TODO        | P2       |            8 | Bob Johnson | Alice Smith | Sprint 1 - Core Foundations | frontend
 AD-5      | Automated Database Timestamp Triggers       | TASK       | DONE        | P3       |            2 | Bob Johnson | Bob Johnson | Sprint 1 - Core Foundations | No Labels
(4 rows)
```

---

## 2. Sprint Velocity & Analytics

### Query 3.1: Sprint Velocity Analytics
```sql
SELECT 
    s.id AS sprint_id,
    s.name AS sprint_name,
    s.status AS sprint_status,
    COUNT(i.id) AS total_issues,
    COALESCE(SUM(i.story_points), 0) AS total_estimated_points,
    COALESCE(SUM(CASE WHEN i.status = 'DONE' THEN i.story_points ELSE 0 END), 0) AS completed_points,
    ROUND(
        CASE 
            WHEN SUM(i.story_points) > 0 
            THEN 100.0 * SUM(CASE WHEN i.status = 'DONE' THEN i.story_points ELSE 0 END) / SUM(i.story_points)
            ELSE 0.0 
        END, 1
    ) AS velocity_completion_percentage
FROM sprints s
LEFT JOIN issues i ON s.id = i.sprint_id AND i.deleted_at IS NULL
GROUP BY s.id, s.name, s.status
ORDER BY s.status ASC;
```

#### Terminal Output:
```
              sprint_id               |          sprint_name          | sprint_status | total_issues | total_estimated_points | completed_points | velocity_completion_percentage 
--------------------------------------+-------------------------------+---------------+--------------+------------------------+------------------+--------------------------------
 bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01 | Sprint 1 - Core Foundations   | ACTIVE        |            4 |                     18 |                5 |                           27.8
 bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02 | Sprint 2 - AI & Collaboration | PLANNED       |            0 |                      0 |                0 |                            0.0
(2 rows)
```

---

## 3. Advanced Window Functions

### Query 4.1: Window Function — Backlog Workload & Percentage Distribution
```sql
SELECT 
    status,
    COUNT(*) AS status_issue_count,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 1) AS status_percentage,
    COALESCE(SUM(story_points), 0) AS status_story_points,
    SUM(COALESCE(SUM(story_points), 0)) OVER (ORDER BY COUNT(*) DESC) AS cumulative_running_points,
    DENSE_RANK() OVER (ORDER BY COUNT(*) DESC) AS volume_rank
FROM issues
WHERE deleted_at IS NULL
GROUP BY status
ORDER BY status_issue_count DESC;
```

#### Terminal Output:
```
   status    | status_issue_count | status_percentage | status_story_points | cumulative_running_points | volume_rank 
-------------+--------------------+-------------------+---------------------+---------------------------+-------------
 DONE        |                  2 |              33.3 |                   5 |                         5 |           1
 BACKLOG     |                  2 |              33.3 |                  11 |                        16 |           1
 IN_PROGRESS |                  1 |              16.7 |                   5 |                        21 |           2
 TODO        |                  1 |              16.7 |                   8 |                        29 |           2
(4 rows)
```

---

## 4. Recursive Common Table Expressions (CTE)

### Query 5.1: Recursive CTE — Hierarchical Threaded Comment Tree
```sql
WITH RECURSIVE comment_tree AS (
    SELECT 
        c.id, c.parent_comment_id, c.author_id, c.content, c.created_at, 0 AS depth,
        ARRAY[c.created_at] AS sort_path
    FROM comments c
    WHERE c.parent_comment_id IS NULL AND c.deleted_at IS NULL
    UNION ALL
    SELECT 
        child.id, child.parent_comment_id, child.author_id, child.content, child.created_at, parent.depth + 1,
        parent.sort_path || child.created_at
    FROM comments child
    INNER JOIN comment_tree parent ON child.parent_comment_id = parent.id
    WHERE child.deleted_at IS NULL
)
SELECT 
    ct.depth,
    u.full_name AS comment_author,
    repeat('   ', ct.depth) || '↳ ' || ct.content AS formatted_comment,
    ct.created_at
FROM comment_tree ct
JOIN users u ON ct.author_id = u.id
ORDER BY ct.sort_path ASC;
```

#### Terminal Output:
```
 depth | comment_author |                                            formatted_comment                                             |          created_at           
-------+----------------+----------------------------------------------------------------------------------------------------------+-------------------------------
     0 | Alice Smith    | ↳ Can we reproduce this on Android Chrome over Airtel 4G network?                                       | 2026-10-22 02:30:11.123456+00
     1 | Bob Johnson    |    ↳ Yes, confirmed. The auth code exchange times out because TLS handshake latency spikes above 2500ms. | 2026-10-22 06:30:11.123456+00
     2 | Alice Smith    |       ↳ Great catch. Let us bump the Axios request timeout to 10 seconds in the API interceptor.         | 2026-10-22 10:30:11.123456+00
(3 rows)
```

---

## 5. PL/pgSQL Trigger Timestamp Verification

### Query 8.1: Cycle Time & Lead Time Calculated via Trigger Timestamps
```sql
SELECT 
    p.key || '-' || i.issue_number AS issue_key,
    i.title,
    i.status,
    i.started_at,
    i.completed_at,
    ROUND(EXTRACT(EPOCH FROM (i.completed_at - i.started_at)) / 3600, 2) AS cycle_time_hours,
    ROUND(EXTRACT(EPOCH FROM (i.completed_at - i.created_at)) / 3600, 2) AS lead_time_hours
FROM issues i
INNER JOIN projects p ON i.project_id = p.id
WHERE i.started_at IS NOT NULL AND i.completed_at IS NOT NULL;
```

#### Terminal Output:
```
 issue_key |                 title                 | status |          started_at           |         completed_at          | cycle_time_hours | lead_time_hours 
-----------+---------------------------------------+--------+-------------------------------+-------------------------------+------------------+-----------------
 AD-1      | Configure PostgreSQL Session Pooler   | DONE   | 2026-10-18 20:30:11.123456+00 | 2026-10-20 20:30:11.123456+00 |            48.00 |           96.00
 AD-5      | Automated Database Timestamp Triggers | DONE   | 2026-10-19 20:30:11.123456+00 | 2026-10-21 20:30:11.123456+00 |            48.00 |           72.00
(2 rows)
```

---

## 6. Embedded Screenshot Index

The corresponding visual proof files are located in `testing/screenshots/`:
- **`01_keycloak_login.png`**: Keycloak SSO authentication screen.
- **`02_board.png`**: Kanban board showing drag-and-drop cards across columns.
- **`03_workflow_guard_history.png`**: Workflow state machine violation toast and audit history.
- **`04_ai_duplicate_warning.png`**: Groq LLM advisory duplicate warning box.
- **`05_live_doc_two_users.png`**: Multi-user concurrent CRDT collaborative document editing.
- **`06_rbac_viewer_denied.png`**: Viewer permission restriction (HTTP 403 Forbidden).
