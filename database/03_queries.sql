-- ==============================================================================
-- AGILIFY — DOCUMENTED COMPLEX SQL QUERIES SCRIPT
-- ==============================================================================
-- Course: 22AIE303 Database Management Systems
-- Project: Agilify (Agile Project Management Platform)
-- Database Engine: PostgreSQL 15+
-- Description: Comprehensive set of real-world analytical queries demonstrating:
--              1. Multi-Table Joins (Inner, Left, Cross joins)
--              2. Nested & Correlated Subqueries
--              3. GROUP BY and Aggregate Functions with HAVING
--              4. Advanced Window Functions (OVER, DENSE_RANK, Running Totals)
--              5. Recursive Common Table Expressions (Hierarchical Comment Trees)
--              6. Set Operations (UNION, EXCEPT)
--              7. GIN Index Full-Text Search
-- ==============================================================================

-- ==============================================================================
-- 1. MULTI-TABLE JOINS
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- Query 1.1: 5-Table Master Join — Active Sprint Kanban Board Card Details
-- Purpose: Retrieves complete card metadata for all issues in the active sprint,
--          including project key, assigned developer name, reporter name,
--          sprint title, and aggregated colored label tags in a single query pass.
-- ------------------------------------------------------------------------------
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
WHERE s.status = 'ACTIVE'
  AND i.deleted_at IS NULL
GROUP BY 
    p.key, i.issue_number, i.title, i.issue_type, i.status, 
    i.priority, i.story_points, u_assignee.full_name, u_reporter.full_name, s.name
ORDER BY i.board_position ASC, i.issue_number ASC;

-- ------------------------------------------------------------------------------
-- Query 1.2: 3-Table Audit Join — Complete Field Modification History
-- Purpose: Generates a human-readable audit trail showing who modified which issue,
--          what field was changed, previous value, new value, and timestamp.
-- ------------------------------------------------------------------------------
SELECT 
    p.key || '-' || i.issue_number AS issue_key,
    i.title AS issue_title,
    u.full_name AS modified_by,
    h.field_name,
    h.old_value,
    h.new_value,
    h.change_reason,
    h.changed_at
FROM issue_history h
INNER JOIN issues i ON h.issue_id = i.id
INNER JOIN projects p ON i.project_id = p.id
INNER JOIN users u ON h.changed_by = u.id
ORDER BY h.changed_at DESC;

-- ==============================================================================
-- 2. NESTED & CORRELATED SUBQUERIES
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- Query 2.1: Correlated Subquery — Developers Assigned Above-Average Story Points
-- Purpose: Identifies high-workload developers whose total assigned story points
--          exceed the average assigned points per developer across the active project.
-- ------------------------------------------------------------------------------
SELECT 
    u.id AS user_id,
    u.full_name,
    u.email,
    SUM(i.story_points) AS total_assigned_points
FROM users u
INNER JOIN issues i ON u.id = i.assignee_id
WHERE i.deleted_at IS NULL
  AND i.sprint_id IS NOT NULL
GROUP BY u.id, u.full_name, u.email
HAVING SUM(i.story_points) > (
    -- Scalar subquery calculating average story points per developer
    SELECT AVG(dev_points) 
    FROM (
        SELECT SUM(sub_i.story_points) AS dev_points
        FROM issues sub_i
        WHERE sub_i.assignee_id IS NOT NULL 
          AND sub_i.deleted_at IS NULL
        GROUP BY sub_i.assignee_id
    ) AS point_distribution
);

-- ------------------------------------------------------------------------------
-- Query 2.2: NOT EXISTS Subquery — Issues With Zero Comments
-- Purpose: Finds unattended critical or high-priority issues that have received
--          zero comments or peer reviews from team members.
-- ------------------------------------------------------------------------------
SELECT 
    p.key || '-' || i.issue_number AS issue_key,
    i.title,
    i.issue_type,
    i.priority,
    i.status,
    i.created_at
FROM issues i
INNER JOIN projects p ON i.project_id = p.id
WHERE i.priority IN ('P1', 'P2')
  AND i.deleted_at IS NULL
  AND NOT EXISTS (
      -- Correlated subquery checking existence of comments
      SELECT 1 
      FROM comments c 
      WHERE c.issue_id = i.id 
        AND c.deleted_at IS NULL
  );

-- ==============================================================================
-- 3. GROUP BY & AGGREGATE FUNCTIONS WITH HAVING
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- Query 3.1: Sprint Velocity & Completion Analytics
-- Purpose: Aggregates total issues, estimated story points, completed story points,
--          and completion percentage for every sprint in the system.
-- ------------------------------------------------------------------------------
SELECT 
    s.id AS sprint_id,
    s.name AS sprint_name,
    s.status AS sprint_status,
    s.start_date,
    s.end_date,
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
GROUP BY s.id, s.name, s.status, s.start_date, s.end_date
ORDER BY s.start_date ASC;

-- ------------------------------------------------------------------------------
-- Query 3.2: Hotspot Analysis — Issues With More Than 2 Watchers (HAVING clause)
-- Purpose: Discovers high-visibility issues with strong team engagement where
--          subscriber count exceeds the threshold.
-- ------------------------------------------------------------------------------
SELECT 
    p.key || '-' || i.issue_number AS issue_key,
    i.title,
    i.status,
    i.priority,
    COUNT(w.user_id) AS total_watchers
FROM issues i
INNER JOIN projects p ON i.project_id = p.id
INNER JOIN issue_watchers w ON i.id = w.issue_id
WHERE i.deleted_at IS NULL
GROUP BY p.key, i.issue_number, i.title, i.status, i.priority
HAVING COUNT(w.user_id) >= 2
ORDER BY total_watchers DESC;

-- ==============================================================================
-- 4. ADVANCED WINDOW FUNCTIONS
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- Query 4.1: Window Function — Backlog Workload & Percentile Analytics
-- Purpose: Computes relative issue volume percentage and running cumulative points
--          across workflow states in a single query pass without sub-selects.
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- Query 4.2: Window Function with PARTITION BY — Issue Ranking Within Priority
-- Purpose: Uses ROW_NUMBER() and DENSE_RANK() partitioned by issue priority to
--          rank issues by estimated complexity (story points) within each tier.
-- ------------------------------------------------------------------------------
SELECT 
    p.key || '-' || i.issue_number AS issue_key,
    i.title,
    i.priority,
    i.story_points,
    ROW_NUMBER() OVER (
        PARTITION BY i.priority 
        ORDER BY i.story_points DESC NULLS LAST, i.created_at ASC
    ) AS priority_rank,
    SUM(i.story_points) OVER (
        PARTITION BY i.priority
    ) AS total_points_in_priority_tier
FROM issues i
INNER JOIN projects p ON i.project_id = p.id
WHERE i.deleted_at IS NULL
ORDER BY i.priority ASC, priority_rank ASC;

-- ==============================================================================
-- 5. RECURSIVE COMMON TABLE EXPRESSIONS (CTE)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- Query 5.1: Recursive CTE — Hierarchical Threaded Comment Tree
-- Purpose: Traverses nested reply trees of arbitrary depth on an issue without
--          incurring application-layer recursive queries. Reconstructs full
--          chronological discussion threads with indentation visual depth.
-- ------------------------------------------------------------------------------
WITH RECURSIVE comment_tree AS (
    -- Anchor Member: Root-level comments (parent_comment_id IS NULL)
    SELECT 
        c.id, 
        c.parent_comment_id, 
        c.author_id, 
        c.content, 
        c.created_at, 
        0 AS depth,
        ARRAY[c.created_at] AS sort_path
    FROM comments c
    WHERE c.parent_comment_id IS NULL 
      AND c.deleted_at IS NULL

    UNION ALL

    -- Recursive Member: Nested reply comments
    SELECT 
        child.id, 
        child.parent_comment_id, 
        child.author_id, 
        child.content, 
        child.created_at, 
        parent.depth + 1,
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

-- ==============================================================================
-- 6. SET OPERATIONS
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- Query 6.1: EXCEPT Set Operation — Users with No Issue Assignments
-- Purpose: Computes the set of registered users who are currently NOT assigned
--          to any active task or bug.
-- ------------------------------------------------------------------------------
SELECT id, full_name, email FROM users WHERE status = 'ACTIVE' AND deleted_at IS NULL
EXCEPT
SELECT u.id, u.full_name, u.email 
FROM users u 
INNER JOIN issues i ON u.id = i.assignee_id 
WHERE i.deleted_at IS NULL;

-- ==============================================================================
-- 7. FULL-TEXT SEARCH (GIN INDEX UTILIZATION)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- Query 7.1: Lexical Ranked Text Search
-- Purpose: Uses PostgreSQL GIN full-text index with ts_rank to search for issues
--          matching terms like 'OAuth' or 'timeout', ranked by keyword relevance.
-- ------------------------------------------------------------------------------
SELECT 
    p.key || '-' || i.issue_number AS issue_key,
    i.title,
    i.description,
    ts_rank(to_tsvector('english', i.title || ' ' || COALESCE(i.description, '')), query) AS relevance_score
FROM issues i
INNER JOIN projects p ON i.project_id = p.id,
to_tsquery('english', 'OAuth | timeout') query
WHERE to_tsvector('english', i.title || ' ' || COALESCE(i.description, '')) @@ query
  AND i.deleted_at IS NULL
ORDER BY relevance_score DESC;

-- ==============================================================================
-- 8. TRIGGER VERIFICATION QUERY
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- Query 8.1: Cycle Time & Lead Time Metrics Calculated via Trigger Timestamps
-- Purpose: Verifies that the PL/pgSQL trigger `trg_issues_timestamps` recorded
--          `started_at` and `completed_at`, computing real engineering cycle times.
-- ------------------------------------------------------------------------------
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
WHERE i.started_at IS NOT NULL 
  AND i.completed_at IS NOT NULL;
