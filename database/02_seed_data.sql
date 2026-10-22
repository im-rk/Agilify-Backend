-- ==============================================================================
-- AGILIFY — SAMPLE SEED DATA SCRIPT
-- ==============================================================================
-- Course: 22AIE303 Database Management Systems
-- Project: Agilify
-- Database Engine: PostgreSQL 15+
-- Description: Inserts realistic demonstration data into all 14 tables to allow
--              immediate evaluation of relational integrity, queries, and reports.
-- ==============================================================================

-- Clean existing data (in dependency order)
TRUNCATE TABLE 
    doc_snapshots, project_docs, notifications, comments, 
    issue_watchers, issue_labels, labels, issue_history, 
    issues, sprints, workflow_config, project_members, 
    projects, users 
CASCADE;

-- ------------------------------------------------------------------------------
-- 1. USERS (Alice: Admin, Bob: Developer, Carol: Viewer)
-- ------------------------------------------------------------------------------
INSERT INTO users (id, email, full_name, display_name, avatar_url, status, password_hash) VALUES
('11111111-1111-1111-1111-111111111111', 'alice@example.com', 'Alice Smith', 'alice', 'https://avatar.iran.liara.run/public/1', 'ACTIVE', 'MANAGED_BY_KEYCLOAK'),
('22222222-2222-2222-2222-222222222222', 'bob@example.com', 'Bob Johnson', 'bob', 'https://avatar.iran.liara.run/public/2', 'ACTIVE', 'MANAGED_BY_KEYCLOAK'),
('33333333-3333-3333-3333-333333333333', 'carol@example.com', 'Carol Williams', 'carol', 'https://avatar.iran.liara.run/public/3', 'ACTIVE', 'MANAGED_BY_KEYCLOAK');

-- ------------------------------------------------------------------------------
-- 2. PROJECTS
-- ------------------------------------------------------------------------------
INSERT INTO projects (id, name, key, description, icon_url, status, created_by) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Agile Delivery Platform', 'AD', 'Next-generation Jira clone with CRDT documents and AI duplicate detection', 'https://api.dicebear.com/7.x/identicon/svg?seed=AD', 'ACTIVE', '11111111-1111-1111-1111-111111111111');

-- ------------------------------------------------------------------------------
-- 3. PROJECT_MEMBERS (Three-tier RBAC)
-- ------------------------------------------------------------------------------
INSERT INTO project_members (project_id, user_id, role) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'ADMIN'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '22222222-2222-2222-2222-222222222222', 'DEVELOPER'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '33333333-3333-3333-3333-333333333333', 'VIEWER');

-- ------------------------------------------------------------------------------
-- 4. WORKFLOW_CONFIG (State Machine Transitions)
-- ------------------------------------------------------------------------------
INSERT INTO workflow_config (project_id, issue_type, from_state, to_state, required_role) VALUES
-- Transitions for BUG
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'BUG', 'BACKLOG', 'TODO', 'DEVELOPER'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'BUG', 'TODO', 'IN_PROGRESS', 'DEVELOPER'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'BUG', 'IN_PROGRESS', 'IN_REVIEW', 'DEVELOPER'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'BUG', 'IN_REVIEW', 'DONE', 'DEVELOPER'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'BUG', 'DONE', 'TODO', 'ADMIN'),
-- Transitions for STORY
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'STORY', 'BACKLOG', 'TODO', 'DEVELOPER'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'STORY', 'TODO', 'IN_PROGRESS', 'DEVELOPER'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'STORY', 'IN_PROGRESS', 'IN_REVIEW', 'DEVELOPER'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'STORY', 'IN_REVIEW', 'DONE', 'DEVELOPER'),
-- Transitions for TASK
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'TASK', 'BACKLOG', 'TODO', 'DEVELOPER'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'TASK', 'TODO', 'IN_PROGRESS', 'DEVELOPER'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'TASK', 'IN_PROGRESS', 'DONE', 'DEVELOPER');

-- ------------------------------------------------------------------------------
-- 5. SPRINTS (One ACTIVE, One PLANNED)
-- ------------------------------------------------------------------------------
INSERT INTO sprints (id, project_id, name, goal, status, start_date, end_date, created_by) VALUES
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Sprint 1 - Core Foundations', 'Establish database migrations, auth flow, and basic board', 'ACTIVE', CURRENT_DATE - INTERVAL '5 days', CURRENT_DATE + INTERVAL '9 days', '11111111-1111-1111-1111-111111111111'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Sprint 2 - AI & Collaboration', 'Integrate Groq LLM duplicate detection and Yjs CRDT docs', 'PLANNED', CURRENT_DATE + INTERVAL '10 days', CURRENT_DATE + INTERVAL '24 days', '11111111-1111-1111-1111-111111111111');

-- ------------------------------------------------------------------------------
-- 6. LABELS
-- ------------------------------------------------------------------------------
INSERT INTO labels (id, project_id, name, colour) VALUES
('cccccccc-cccc-cccc-cccc-cccccccc0001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'backend', '#3b82f6'),
('cccccccc-cccc-cccc-cccc-cccccccc0002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'frontend', '#10b981'),
('cccccccc-cccc-cccc-cccc-cccccccc0003', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'security', '#ef4444'),
('cccccccc-cccc-cccc-cccc-cccccccc0004', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'dbms', '#8b5cf6'),
('cccccccc-cccc-cccc-cccc-cccccccc0005', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'critical', '#f97316');

-- ------------------------------------------------------------------------------
-- 7. ISSUES
-- ------------------------------------------------------------------------------
INSERT INTO issues (id, project_id, issue_number, issue_type, title, description, status, priority, story_points, board_position, assignee_id, reporter_id, sprint_id, version, started_at, completed_at) VALUES
('dddddddd-dddd-dddd-dddd-dddddddd0001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 1, 'TASK', 'Configure PostgreSQL Session Pooler', 'Set up Supabase pooler on port 5432 over SSL for Flyway advisory lock compatibility', 'DONE', 'P1', 3, 1, '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', 1, NOW() - INTERVAL '4 days', NOW() - INTERVAL '2 days'),
('dddddddd-dddd-dddd-dddd-dddddddd0002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 2, 'BUG', 'OAuth Callback Timeout on Mobile Networks', 'PKCE code exchange intermittently fails when latency exceeds 3000ms on cellular data', 'IN_PROGRESS', 'P1', 5, 1, '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', 2, NOW() - INTERVAL '1 day', NULL),
('dddddddd-dddd-dddd-dddd-dddddddd0003', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 3, 'STORY', 'Implement Kanban Card Drag-and-Drop', 'Enable developers to transition issues between columns using accessible dnd-kit components', 'TODO', 'P2', 8, 1, '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', 0, NULL, NULL),
('dddddddd-dddd-dddd-dddd-dddddddd0004', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 4, 'STORY', 'Real-Time Yjs Document Collaborative Editing', 'Integrate WebSocket binary relay with Yjs CRDT for live multi-user project specifications', 'BACKLOG', 'P2', 8, 2, NULL, '11111111-1111-1111-1111-111111111111', NULL, 0, NULL, NULL),
('dddddddd-dddd-dddd-dddd-dddddddd0005', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 5, 'TASK', 'Automated Database Timestamp Triggers', 'Create PL/pgSQL trigger set_issue_timestamps to record cycle time metrics', 'DONE', 'P3', 2, 2, '22222222-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', 1, NOW() - INTERVAL '3 days', NOW() - INTERVAL '1 day'),
('dddddddd-dddd-dddd-dddd-dddddddd0006', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 6, 'BUG', 'Groq API Rate Limit Fallback Advisory', 'When Groq LLM returns HTTP 429, handle gracefully without blocking user issue creation', 'BACKLOG', 'P3', 3, 3, NULL, '33333333-3333-3333-3333-333333333333', NULL, 0, NULL, NULL);

-- ------------------------------------------------------------------------------
-- 8. ISSUE_LABELS (Many-to-Many Links)
-- ------------------------------------------------------------------------------
INSERT INTO issue_labels (issue_id, label_id) VALUES
('dddddddd-dddd-dddd-dddd-dddddddd0001', 'cccccccc-cccc-cccc-cccc-cccccccc0001'),
('dddddddd-dddd-dddd-dddd-dddddddd0001', 'cccccccc-cccc-cccc-cccc-cccccccc0004'),
('dddddddd-dddd-dddd-dddd-dddddddd0002', 'cccccccc-cccc-cccc-cccc-cccccccc0001'),
('dddddddd-dddd-dddd-dddd-dddddddd0002', 'cccccccc-cccc-cccc-cccc-cccccccc0003'),
('dddddddd-dddd-dddd-dddd-dddddddd0002', 'cccccccc-cccc-cccc-cccc-cccccccc0005'),
('dddddddd-dddd-dddd-dddd-dddddddd0003', 'cccccccc-cccc-cccc-cccc-cccccccc0002'),
('dddddddd-dddd-dddd-dddd-dddddddd0004', 'cccccccc-cccc-cccc-cccc-cccccccc0001'),
('dddddddd-dddd-dddd-dddd-dddddddd0004', 'cccccccc-cccc-cccc-cccc-cccccccc0002');

-- ------------------------------------------------------------------------------
-- 9. ISSUE_WATCHERS (Subscriptions)
-- ------------------------------------------------------------------------------
INSERT INTO issue_watchers (issue_id, user_id) VALUES
('dddddddd-dddd-dddd-dddd-dddddddd0001', '11111111-1111-1111-1111-111111111111'),
('dddddddd-dddd-dddd-dddd-dddddddd0001', '22222222-2222-2222-2222-222222222222'),
('dddddddd-dddd-dddd-dddd-dddddddd0002', '11111111-1111-1111-1111-111111111111'),
('dddddddd-dddd-dddd-dddd-dddddddd0002', '22222222-2222-2222-2222-222222222222'),
('dddddddd-dddd-dddd-dddd-dddddddd0002', '33333333-3333-3333-3333-333333333333');

-- ------------------------------------------------------------------------------
-- 10. ISSUE_HISTORY (Immutable Audit Records)
-- ------------------------------------------------------------------------------
INSERT INTO issue_history (issue_id, changed_by, field_name, old_value, new_value, change_reason, changed_at) VALUES
('dddddddd-dddd-dddd-dddd-dddddddd0001', '22222222-2222-2222-2222-222222222222', 'status', 'TODO', 'IN_PROGRESS', 'Started working on session pooler config', NOW() - INTERVAL '4 days'),
('dddddddd-dddd-dddd-dddd-dddddddd0001', '22222222-2222-2222-2222-222222222222', 'status', 'IN_PROGRESS', 'DONE', 'Pooler verified over SSL', NOW() - INTERVAL '2 days'),
('dddddddd-dddd-dddd-dddd-dddddddd0002', '22222222-2222-2222-2222-222222222222', 'status', 'TODO', 'IN_PROGRESS', 'Investigating PKCE token timeout', NOW() - INTERVAL '1 day');

-- ------------------------------------------------------------------------------
-- 11. COMMENTS (Threaded Hierarchy: Parent and Child Reply)
-- ------------------------------------------------------------------------------
INSERT INTO comments (id, issue_id, author_id, parent_comment_id, content, created_at) VALUES
('eeeeeeee-eeee-eeee-eeee-eeeeeeee0001', 'dddddddd-dddd-dddd-dddd-dddddddd0002', '11111111-1111-1111-1111-111111111111', NULL, 'Can we reproduce this on Android Chrome over Airtel 4G network?', NOW() - INTERVAL '18 hours'),
('eeeeeeee-eeee-eeee-eeee-eeeeeeee0002', 'dddddddd-dddd-dddd-dddd-dddddddd0002', '22222222-2222-2222-2222-222222222222', 'eeeeeeee-eeee-eeee-eeee-eeeeeeee0001', 'Yes, confirmed. The auth code exchange times out because TLS handshake latency spikes above 2500ms.', NOW() - INTERVAL '14 hours'),
('eeeeeeee-eeee-eeee-eeee-eeeeeeee0003', 'dddddddd-dddd-dddd-dddd-dddddddd0002', '11111111-1111-1111-1111-111111111111', 'eeeeeeee-eeee-eeee-eeee-eeeeeeee0002', 'Great catch. Let us bump the Axios request timeout to 10 seconds in the API interceptor.', NOW() - INTERVAL '10 hours');

-- ------------------------------------------------------------------------------
-- 12. NOTIFICATIONS
-- ------------------------------------------------------------------------------
INSERT INTO notifications (recipient_id, type, title, body, referenced_type, referenced_id, project_key, is_read) VALUES
('22222222-2222-2222-2222-222222222222', 'ISSUE_ASSIGNED', 'Assigned: AD-2', 'Alice assigned you to AD-2: OAuth Callback Timeout on Mobile Networks', 'ISSUE', 'dddddddd-dddd-dddd-dddd-dddddddd0002', 'AD', TRUE),
('11111111-1111-1111-1111-111111111111', 'COMMENT_ADDED', 'New reply on AD-2', 'Bob Johnson replied to your comment on AD-2', 'ISSUE', 'dddddddd-dddd-dddd-dddd-dddddddd0002', 'AD', FALSE);

-- ------------------------------------------------------------------------------
-- 13. PROJECT_DOCS & 14. DOC_SNAPSHOTS
-- ------------------------------------------------------------------------------
INSERT INTO project_docs (id, project_id, title, created_by) VALUES
('ffffffff-ffff-ffff-ffff-ffffffff0001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'System Architecture & Security Specification', '11111111-1111-1111-1111-111111111111');

INSERT INTO doc_snapshots (doc_id, snapshot, version) VALUES
('ffffffff-ffff-ffff-ffff-ffffffff0001', decode('0102030405060708090a0b0c0d0e0f', 'hex'), 1);
