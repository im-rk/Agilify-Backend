-- ==============================================================================
-- AGILIFY — COMPLETE DATABASE SCHEMA DDL & 10+ ROW SEED DATA SCRIPT
-- ==============================================================================
-- Course: 22AIE303 Database Management Systems
-- Project: Agilify (Agile Project Management Platform)
-- Database Engine: PostgreSQL 15+
--
-- Instructions:
-- 1. Open pgAdmin, DBeaver, or psql terminal.
-- 2. Connect to your database (e.g. `bugtracker` or `postgres`).
-- 3. Run this entire script in one execution (Ctrl+A -> Execute / F5).
-- 4. It will cleanly drop existing tables, create all 14 tables with strict
--    constraints/triggers, and insert AT LEAST 10 ROWS for every table.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 0. CLEANUP (Drop tables in reverse dependency order)
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS doc_snapshots CASCADE;
DROP TABLE IF EXISTS project_docs CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS comments CASCADE;
DROP TABLE IF EXISTS issue_watchers CASCADE;
DROP TABLE IF EXISTS issue_labels CASCADE;
DROP TABLE IF EXISTS labels CASCADE;
DROP TABLE IF EXISTS issue_history CASCADE;
DROP TABLE IF EXISTS issues CASCADE;
DROP TABLE IF EXISTS sprints CASCADE;
DROP TABLE IF EXISTS workflow_config CASCADE;
DROP TABLE IF EXISTS project_members CASCADE;
DROP TABLE IF EXISTS projects CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- ------------------------------------------------------------------------------
-- EXTENSIONS & TRIGGER FUNCTIONS
-- ------------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION set_issue_timestamps()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'IN_PROGRESS' AND (OLD.status IS NULL OR OLD.status != 'IN_PROGRESS') AND NEW.started_at IS NULL THEN
        NEW.started_at = NOW();
    END IF;
    IF NEW.status = 'DONE' AND (OLD.status IS NULL OR OLD.status != 'DONE') THEN
        NEW.completed_at = NOW();
    END IF;
    IF NEW.status != 'DONE' AND OLD.status = 'DONE' THEN
        NEW.completed_at = NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ==============================================================================
-- 1. DDL: TABLE DEFINITIONS & CONSTRAINTS (14 TABLES)
-- ==============================================================================

-- TABLE 1: users
CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    avatar_url      TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    password_hash   VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);
CREATE UNIQUE INDEX idx_users_email_active ON users (email) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- TABLE 2: projects
CREATE TABLE projects (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    key             VARCHAR(10)  NOT NULL,
    description     TEXT,
    icon_url        TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by      UUID         NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT projects_key_format  CHECK (key ~ '^[A-Z]{2,10}$'),
    CONSTRAINT projects_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);
CREATE UNIQUE INDEX idx_projects_key_active ON projects (key) WHERE deleted_at IS NULL;
CREATE INDEX idx_projects_created_by ON projects (created_by);
CREATE TRIGGER trg_projects_updated_at BEFORE UPDATE ON projects FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- TABLE 3: project_members
CREATE TABLE project_members (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL DEFAULT 'DEVELOPER',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT project_members_unique UNIQUE (project_id, user_id),
    CONSTRAINT project_members_role_check CHECK (role IN ('ADMIN', 'DEVELOPER', 'VIEWER'))
);
CREATE INDEX idx_project_members_project ON project_members (project_id);
CREATE INDEX idx_project_members_user    ON project_members (user_id);
CREATE TRIGGER trg_project_members_updated_at BEFORE UPDATE ON project_members FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- TABLE 4: workflow_config
CREATE TABLE workflow_config (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    issue_type      VARCHAR(20)  NOT NULL,
    from_state      VARCHAR(50)  NOT NULL,
    to_state        VARCHAR(50)  NOT NULL,
    required_role   VARCHAR(20),
    CONSTRAINT workflow_unique UNIQUE (project_id, issue_type, from_state, to_state),
    CONSTRAINT workflow_issue_type_check CHECK (issue_type IN ('BUG', 'STORY', 'TASK', 'EPIC'))
);
CREATE INDEX idx_workflow_project_type ON workflow_config (project_id, issue_type);

-- TABLE 5: sprints
CREATE TABLE sprints (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    goal            TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    start_date      DATE,
    end_date        DATE,
    closed_at       TIMESTAMPTZ,
    created_by      UUID         NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT sprints_status_check CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED')),
    CONSTRAINT sprints_dates_check CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);
CREATE UNIQUE INDEX idx_sprints_one_active_per_project ON sprints (project_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_sprints_project ON sprints (project_id, status);
CREATE TRIGGER trg_sprints_updated_at BEFORE UPDATE ON sprints FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- TABLE 6: issues
CREATE TABLE issues (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
    issue_number    INTEGER      NOT NULL,
    issue_type      VARCHAR(20)  NOT NULL,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    status          VARCHAR(50)  NOT NULL DEFAULT 'BACKLOG',
    priority        VARCHAR(5)   NOT NULL DEFAULT 'P3',
    story_points    INTEGER,
    board_position  INTEGER      NOT NULL DEFAULT 0,
    assignee_id     UUID         REFERENCES users(id),
    reporter_id     UUID         NOT NULL REFERENCES users(id),
    parent_issue_id UUID         REFERENCES issues(id) ON DELETE SET NULL,
    sprint_id       UUID         REFERENCES sprints(id) ON DELETE SET NULL,
    due_date        DATE,
    custom_fields   JSONB        NOT NULL DEFAULT '{}',
    version         INTEGER      NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT issues_number_unique     UNIQUE (project_id, issue_number),
    CONSTRAINT issues_type_check        CHECK (issue_type IN ('BUG','STORY','TASK','EPIC')),
    CONSTRAINT issues_priority_check    CHECK (priority IN ('P1','P2','P3','P4')),
    CONSTRAINT issues_points_check      CHECK (story_points IS NULL OR story_points >= 0),
    CONSTRAINT issues_version_check     CHECK (version >= 0)
);
CREATE INDEX idx_issues_project_status ON issues (project_id, status, priority) WHERE deleted_at IS NULL;
CREATE INDEX idx_issues_board ON issues (project_id, status, board_position ASC) WHERE deleted_at IS NULL;
CREATE INDEX idx_issues_sprint ON issues (sprint_id, status) WHERE sprint_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_issues_assignee ON issues (assignee_id, status) WHERE assignee_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_issues_fulltext ON issues USING GIN (to_tsvector('english', title || ' ' || COALESCE(description, '')));
CREATE INDEX idx_issues_custom_fields ON issues USING GIN (custom_fields);
CREATE TRIGGER trg_issues_timestamps BEFORE UPDATE ON issues FOR EACH ROW EXECUTE FUNCTION set_issue_timestamps();
CREATE TRIGGER trg_issues_updated_at BEFORE UPDATE ON issues FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- TABLE 7: issue_history
CREATE TABLE issue_history (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    issue_id        UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    changed_by      UUID         NOT NULL REFERENCES users(id),
    field_name      VARCHAR(100) NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    change_reason   TEXT,
    changed_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_issue_history_issue_time ON issue_history (issue_id, changed_at ASC);
CREATE INDEX idx_issue_history_status_changes ON issue_history (field_name, changed_at DESC) WHERE field_name = 'status';

-- TABLE 8: labels
CREATE TABLE labels (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    colour          VARCHAR(7)   NOT NULL DEFAULT '#888888',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT labels_unique UNIQUE (project_id, name)
);
CREATE INDEX idx_labels_project ON labels (project_id);

-- TABLE 9: issue_labels
CREATE TABLE issue_labels (
    issue_id        UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    label_id        UUID         NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    added_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (issue_id, label_id)
);
CREATE INDEX idx_issue_labels_label ON issue_labels (label_id);

-- TABLE 10: issue_watchers
CREATE TABLE issue_watchers (
    issue_id        UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    added_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (issue_id, user_id)
);
CREATE INDEX idx_issue_watchers_user ON issue_watchers (user_id);

-- TABLE 11: comments
CREATE TABLE comments (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    issue_id            UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    author_id           UUID         NOT NULL REFERENCES users(id),
    parent_comment_id   UUID         REFERENCES comments(id) ON DELETE CASCADE,
    content             TEXT         NOT NULL,
    is_edited           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT comments_content_check CHECK (length(trim(content)) > 0)
);
CREATE INDEX idx_comments_issue ON comments (issue_id, created_at ASC) WHERE deleted_at IS NULL;
CREATE INDEX idx_comments_parent ON comments (parent_comment_id) WHERE parent_comment_id IS NOT NULL;
CREATE TRIGGER trg_comments_updated_at BEFORE UPDATE ON comments FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- TABLE 12: notifications
CREATE TABLE notifications (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    recipient_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(50)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    body            TEXT,
    referenced_type VARCHAR(50),
    referenced_id   UUID,
    project_key     VARCHAR(10),
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, is_read, created_at DESC);

-- TABLE 13: project_docs
CREATE TABLE project_docs (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    created_by      UUID         NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_project_docs_project ON project_docs (project_id) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_project_docs_updated_at BEFORE UPDATE ON project_docs FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- TABLE 14: doc_snapshots
CREATE TABLE doc_snapshots (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    doc_id          UUID         NOT NULL REFERENCES project_docs(id) ON DELETE CASCADE,
    snapshot        BYTEA        NOT NULL,
    version         BIGINT       NOT NULL,
    saved_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT doc_snapshots_version_unique UNIQUE (doc_id, version)
);
CREATE INDEX idx_doc_snapshots_doc ON doc_snapshots (doc_id, version ASC);


-- ==============================================================================
-- 2. DML: INSERTING SAMPLE DATA (AT LEAST 10 ROWS PER TABLE)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- Table 1: users (10 Rows)
-- ------------------------------------------------------------------------------
INSERT INTO users (id, email, full_name, display_name, avatar_url, status, password_hash) VALUES
('11111111-1111-1111-1111-111111111101', 'alice@example.com',   'Alice Smith',       'alice',   'https://avatar.iran.liara.run/public/1',  'ACTIVE', 'MANAGED_BY_KEYCLOAK'),
('11111111-1111-1111-1111-111111111102', 'bob@example.com',     'Bob Johnson',       'bob',     'https://avatar.iran.liara.run/public/2',  'ACTIVE', 'MANAGED_BY_KEYCLOAK'),
('11111111-1111-1111-1111-111111111103', 'carol@example.com',   'Carol Williams',     'carol',   'https://avatar.iran.liara.run/public/3',  'ACTIVE', 'MANAGED_BY_KEYCLOAK'),
('11111111-1111-1111-1111-111111111104', 'david@example.com',   'David Brown',       'david',   'https://avatar.iran.liara.run/public/4',  'ACTIVE', 'MANAGED_BY_KEYCLOAK'),
('11111111-1111-1111-1111-111111111105', 'emma@example.com',    'Emma Davis',        'emma',    'https://avatar.iran.liara.run/public/5',  'ACTIVE', 'MANAGED_BY_KEYCLOAK'),
('11111111-1111-1111-1111-111111111106', 'frank@example.com',   'Frank Miller',      'frank',   'https://avatar.iran.liara.run/public/6',  'ACTIVE', 'MANAGED_BY_KEYCLOAK'),
('11111111-1111-1111-1111-111111111107', 'grace@example.com',   'Grace Wilson',      'grace',   'https://avatar.iran.liara.run/public/7',  'ACTIVE', 'MANAGED_BY_KEYCLOAK'),
('11111111-1111-1111-1111-111111111108', 'henry@example.com',   'Henry Moore',       'henry',   'https://avatar.iran.liara.run/public/8',  'ACTIVE', 'MANAGED_BY_KEYCLOAK'),
('11111111-1111-1111-1111-111111111109', 'ivy@example.com',     'Ivy Taylor',        'ivy',     'https://avatar.iran.liara.run/public/9',  'ACTIVE', 'MANAGED_BY_KEYCLOAK'),
('11111111-1111-1111-1111-111111111110', 'jack@example.com',    'Jack Anderson',     'jack',    'https://avatar.iran.liara.run/public/10', 'SUSPENDED', 'MANAGED_BY_KEYCLOAK');

-- ------------------------------------------------------------------------------
-- Table 2: projects (10 Rows)
-- ------------------------------------------------------------------------------
INSERT INTO projects (id, name, key, description, icon_url, status, created_by) VALUES
('22222222-2222-2222-2222-222222222201', 'Agile Delivery Platform',       'AD',     'Next-gen Jira platform with CRDT docs and AI advisory', 'https://api.dicebear.com/7.x/identicon/svg?seed=AD',    'ACTIVE', '11111111-1111-1111-1111-111111111101'),
('22222222-2222-2222-2222-222222222202', 'Cloud Infrastructure Service',  'CIS',    'AWS free-tier automated orchestration and IaC management', 'https://api.dicebear.com/7.x/identicon/svg?seed=CIS',   'ACTIVE', '11111111-1111-1111-1111-111111111101'),
('22222222-2222-2222-2222-222222222203', 'Payment Gateway Integration',   'PAY',    'Stripe and PayPal multi-currency checkout microservice', 'https://api.dicebear.com/7.x/identicon/svg?seed=PAY',   'ACTIVE', '11111111-1111-1111-1111-111111111102'),
('22222222-2222-2222-2222-222222222204', 'Machine Learning Pipeline',     'MLP',    'Vector embeddings and duplicate detection pipeline',    'https://api.dicebear.com/7.x/identicon/svg?seed=MLP',   'ACTIVE', '11111111-1111-1111-1111-111111111104'),
('22222222-2222-2222-2222-222222222205', 'Identity & Access Manager',     'IAM',    'Keycloak SSO and OAuth2 PKCE security realm',          'https://api.dicebear.com/7.x/identicon/svg?seed=IAM',   'ACTIVE', '11111111-1111-1111-1111-111111111101'),
('22222222-2222-2222-2222-222222222206', 'Inventory Management System',  'INV',    'Warehouse stock tracking and barcode scanning engine',  'https://api.dicebear.com/7.x/identicon/svg?seed=INV',   'ACTIVE', '11111111-1111-1111-1111-111111111105'),
('22222222-2222-2222-2222-222222222207', 'Customer Relationship Portal',  'CRM',    'Client ticket management and support ticketing portal', 'https://api.dicebear.com/7.x/identicon/svg?seed=CRM',   'ACTIVE', '11111111-1111-1111-1111-111111111106'),
('22222222-2222-2222-2222-222222222208', 'Real-Time Notification Hub',    'NOTIF',  'WebSocket and push notification fanout broker',         'https://api.dicebear.com/7.x/identicon/svg?seed=NOTIF', 'ACTIVE', '11111111-1111-1111-1111-111111111102'),
('22222222-2222-2222-2222-222222222209', 'Data Analytics & Warehouse',    'DATA',   'PostgreSQL ETL pipelines and business intelligence ETL', 'https://api.dicebear.com/7.x/identicon/svg?seed=DATA',  'ACTIVE', '11111111-1111-1111-1111-111111111107'),
('22222222-2222-2222-2222-222222222210', 'Legacy Bugtracker Migration',   'LEGACY', 'Historical ticket archive from MantisBT 2020',          'https://api.dicebear.com/7.x/identicon/svg?seed=LEG',   'ARCHIVED', '11111111-1111-1111-1111-111111111101');

-- ------------------------------------------------------------------------------
-- Table 3: project_members (15 Rows)
-- ------------------------------------------------------------------------------
INSERT INTO project_members (project_id, user_id, role) VALUES
('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111101', 'ADMIN'),
('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111102', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111103', 'VIEWER'),
('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111104', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111101', 'ADMIN'),
('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111105', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111102', 'ADMIN'),
('22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111106', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111104', 'ADMIN'),
('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111107', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111101', 'ADMIN'),
('22222222-2222-2222-2222-222222222206', '11111111-1111-1111-1111-111111111105', 'ADMIN'),
('22222222-2222-2222-2222-222222222207', '11111111-1111-1111-1111-111111111106', 'ADMIN'),
('22222222-2222-2222-2222-222222222208', '11111111-1111-1111-1111-111111111102', 'ADMIN'),
('22222222-2222-2222-2222-222222222209', '11111111-1111-1111-1111-111111111107', 'ADMIN');

-- ------------------------------------------------------------------------------
-- Table 4: workflow_config (16 Rows)
-- ------------------------------------------------------------------------------
INSERT INTO workflow_config (project_id, issue_type, from_state, to_state, required_role) VALUES
-- Transitions for BUG in project AD
('22222222-2222-2222-2222-222222222201', 'BUG', 'BACKLOG', 'TODO', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222201', 'BUG', 'TODO', 'IN_PROGRESS', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222201', 'BUG', 'IN_PROGRESS', 'IN_REVIEW', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222201', 'BUG', 'IN_REVIEW', 'DONE', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222201', 'BUG', 'DONE', 'TODO', 'ADMIN'),
-- Transitions for STORY in project AD
('22222222-2222-2222-2222-222222222201', 'STORY', 'BACKLOG', 'TODO', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222201', 'STORY', 'TODO', 'IN_PROGRESS', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222201', 'STORY', 'IN_PROGRESS', 'IN_REVIEW', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222201', 'STORY', 'IN_REVIEW', 'DONE', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222201', 'STORY', 'DONE', 'TODO', 'ADMIN'),
-- Transitions for TASK in project AD
('22222222-2222-2222-2222-222222222201', 'TASK', 'BACKLOG', 'TODO', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222201', 'TASK', 'TODO', 'IN_PROGRESS', 'DEVELOPER'),
('22222222-2222-2222-2222-222222222201', 'TASK', 'IN_PROGRESS', 'DONE', 'DEVELOPER'),
-- Transitions for EPIC in project AD
('22222222-2222-2222-2222-222222222201', 'EPIC', 'BACKLOG', 'TODO', 'ADMIN'),
('22222222-2222-2222-2222-222222222201', 'EPIC', 'TODO', 'IN_PROGRESS', 'ADMIN'),
('22222222-2222-2222-2222-222222222201', 'EPIC', 'IN_PROGRESS', 'DONE', 'ADMIN');

-- ------------------------------------------------------------------------------
-- Table 5: sprints (10 Rows across projects - at most 1 ACTIVE per project)
-- ------------------------------------------------------------------------------
INSERT INTO sprints (id, project_id, name, goal, status, start_date, end_date, created_by) VALUES
('33333333-3333-3333-3333-333333333301', '22222222-2222-2222-2222-222222222201', 'Sprint 1 - Core Foundations',    'Establish database schema, Keycloak SSO, and board', 'ACTIVE',  CURRENT_DATE - INTERVAL '6 days', CURRENT_DATE + INTERVAL '8 days',  '11111111-1111-1111-1111-111111111101'),
('33333333-3333-3333-3333-333333333302', '22222222-2222-2222-2222-222222222201', 'Sprint 2 - Collaboration & AI',  'Deliver Yjs CRDT real-time docs and Groq duplicate warning', 'PLANNED', CURRENT_DATE + INTERVAL '9 days', CURRENT_DATE + INTERVAL '23 days', '11111111-1111-1111-1111-111111111101'),
('33333333-3333-3333-3333-333333333303', '22222222-2222-2222-2222-222222222201', 'Sprint 0 - Project Inception',   'Initial architecture spikes and technology review', 'CLOSED',   CURRENT_DATE - INTERVAL '21 days', CURRENT_DATE - INTERVAL '7 days', '11111111-1111-1111-1111-111111111101'),
('33333333-3333-3333-3333-333333333304', '22222222-2222-2222-2222-222222222202', 'CIS Sprint 1 - EC2 Setup',       'Launch t3.micro instance and configure 2GB swap',   'ACTIVE',  CURRENT_DATE - INTERVAL '4 days', CURRENT_DATE + INTERVAL '10 days', '11111111-1111-1111-1111-111111111101'),
('33333333-3333-3333-3333-333333333305', '22222222-2222-2222-2222-222222222202', 'CIS Sprint 2 - Caddy SSL',       'Automate Let''s Encrypt certificates and proxy',    'PLANNED', CURRENT_DATE + INTERVAL '11 days', CURRENT_DATE + INTERVAL '25 days', '11111111-1111-1111-1111-111111111101'),
('33333333-3333-3333-3333-333333333306', '22222222-2222-2222-2222-222222222203', 'PAY Sprint 1 - Stripe SDK',      'Integrate Stripe payment intent webhook listener',  'ACTIVE',  CURRENT_DATE - INTERVAL '3 days', CURRENT_DATE + INTERVAL '11 days', '11111111-1111-1111-1111-111111111102'),
('33333333-3333-3333-3333-333333333307', '22222222-2222-2222-2222-222222222204', 'MLP Sprint 1 - Embeddings',     'Generate 384-dimensional sentence vector embeddings','ACTIVE',  CURRENT_DATE - INTERVAL '2 days', CURRENT_DATE + INTERVAL '12 days', '11111111-1111-1111-1111-111111111104'),
('33333333-3333-3333-3333-333333333308', '22222222-2222-2222-2222-222222222205', 'IAM Sprint 1 - Realm Migration', 'Export local Keycloak realm into production format',  'ACTIVE',  CURRENT_DATE - INTERVAL '5 days', CURRENT_DATE + INTERVAL '9 days',  '11111111-1111-1111-1111-111111111101'),
('33333333-3333-3333-3333-333333333309', '22222222-2222-2222-2222-222222222206', 'INV Sprint 1 - Stock Auditing',  'Implement barcode scanning and inventory adjustment', 'ACTIVE', CURRENT_DATE - INTERVAL '1 days', CURRENT_DATE + INTERVAL '13 days', '11111111-1111-1111-1111-111111111105'),
('33333333-3333-3333-3333-333333333310', '22222222-2222-2222-2222-222222222207', 'CRM Sprint 1 - Ticket Triage',   'Ticket routing engine based on client SLA tier',    'ACTIVE',  CURRENT_DATE - INTERVAL '7 days', CURRENT_DATE + INTERVAL '7 days',  '11111111-1111-1111-1111-111111111106');

-- ------------------------------------------------------------------------------
-- Table 6: issues (12 Rows)
-- ------------------------------------------------------------------------------
INSERT INTO issues (id, project_id, issue_number, issue_type, title, description, status, priority, story_points, board_position, assignee_id, reporter_id, sprint_id, version, started_at, completed_at) VALUES
('44444444-4444-4444-4444-444444444401', '22222222-2222-2222-2222-222222222201', 1,  'TASK',  'Configure PostgreSQL Session Pooler',       'Set up Supabase pooler on port 5432 over SSL for Flyway lock safety', 'DONE',        'P1', 3, 1, '11111111-1111-1111-1111-111111111102', '11111111-1111-1111-1111-111111111101', '33333333-3333-3333-3333-333333333301', 1, NOW() - INTERVAL '5 days', NOW() - INTERVAL '3 days'),
('44444444-4444-4444-4444-444444444402', '22222222-2222-2222-2222-222222222201', 2,  'BUG',   'OAuth Callback Timeout on Mobile Networks', 'PKCE code exchange intermittently fails on high-latency 4G cellular links', 'IN_PROGRESS','P1', 5, 1, '11111111-1111-1111-1111-111111111102', '11111111-1111-1111-1111-111111111101', '33333333-3333-3333-3333-333333333301', 2, NOW() - INTERVAL '2 days', NULL),
('44444444-4444-4444-4444-444444444403', '22222222-2222-2222-2222-222222222201', 3,  'STORY', 'Implement Kanban Card Drag-and-Drop',       'Build accessible drag-and-drop board cards with dnd-kit and touch support', 'TODO',       'P2', 8, 1, '11111111-1111-1111-1111-111111111102', '11111111-1111-1111-1111-111111111101', '33333333-3333-3333-3333-333333333301', 0, NULL, NULL),
('44444444-4444-4444-4444-444444444404', '22222222-2222-2222-2222-222222222201', 4,  'STORY', 'Real-Time Yjs Document Collaborative Edit', 'Integrate WebSocket binary relay with Yjs CRDT for specification sharing', 'IN_REVIEW',   'P2', 8, 2, '11111111-1111-1111-1111-111111111104', '11111111-1111-1111-1111-111111111101', '33333333-3333-3333-3333-333333333301', 3, NOW() - INTERVAL '3 days', NULL),
('44444444-4444-4444-4444-444444444405', '22222222-2222-2222-2222-222222222201', 5,  'TASK',  'Automated Database Timestamp Triggers',     'Create PL/pgSQL trigger set_issue_timestamps for cycle metrics', 'DONE',        'P3', 2, 2, '11111111-1111-1111-1111-111111111102', '11111111-1111-1111-1111-111111111102', '33333333-3333-3333-3333-333333333301', 1, NOW() - INTERVAL '4 days', NOW() - INTERVAL '2 days'),
('44444444-4444-4444-4444-444444444406', '22222222-2222-2222-2222-222222222201', 6,  'BUG',   'Groq API Rate Limit Fallback Advisory',     'When Groq returns HTTP 429, fail open to avoid blocking issue creation', 'BACKLOG',    'P3', 3, 3, NULL,                               '11111111-1111-1111-1111-111111111103', NULL,                                 0, NULL, NULL),
('44444444-4444-4444-4444-444444444407', '22222222-2222-2222-2222-222222222201', 7,  'STORY', 'Sprint Velocity & Burndown Charts',        'Render burndown charts from issue_history state changes',                'BACKLOG',    'P2', 5, 4, NULL,                               '11111111-1111-1111-1111-111111111101', NULL,                                 0, NULL, NULL),
('44444444-4444-4444-4444-444444444408', '22222222-2222-2222-2222-222222222201', 8,  'BUG',   'CORS Rejection on Preflight OPTIONS',       'Spring Security rejects cross-origin OPTIONS from HTTPS edge proxy',     'DONE',        'P1', 2, 3, '11111111-1111-1111-1111-111111111102', '11111111-1111-1111-1111-111111111101', '33333333-3333-3333-3333-333333333301', 1, NOW() - INTERVAL '6 days', NOW() - INTERVAL '4 days'),
('44444444-4444-4444-4444-444444444409', '22222222-2222-2222-2222-222222222201', 9,  'EPIC',  'Enterprise Authentication & Authorization', 'Complete OIDC PKCE and 3-tier role-based access control architecture', 'IN_PROGRESS','P1',13, 2, '11111111-1111-1111-1111-111111111101', '11111111-1111-1111-1111-111111111101', '33333333-3333-3333-3333-333333333301', 1, NOW() - INTERVAL '6 days', NULL),
('44444444-4444-4444-4444-444444444410', '22222222-2222-2222-2222-222222222201', 10, 'TASK',  'Setup Automated GitHub OIDC AWS Deploy',   'Replace long-lived AWS keys with short-lived STS tokens',               'DONE',        'P2', 5, 4, '11111111-1111-1111-1111-111111111102', '11111111-1111-1111-1111-111111111101', '33333333-3333-3333-3333-333333333301', 1, NOW() - INTERVAL '5 days', NOW() - INTERVAL '3 days'),
('44444444-4444-4444-4444-444444444411', '22222222-2222-2222-2222-222222222202', 1,  'TASK',  'Provision EC2 t3.micro with Ubuntu 22.04',  'Launch AWS instance with 20GB gp3 storage and IAM profile',             'DONE',        'P1', 3, 1, '11111111-1111-1111-1111-111111111105', '11111111-1111-1111-1111-111111111101', '33333333-3333-3333-3333-333333333304', 1, NOW() - INTERVAL '4 days', NOW() - INTERVAL '3 days'),
('44444444-4444-4444-4444-444444444412', '22222222-2222-2222-2222-222222222203', 1,  'TASK',  'Configure Stripe Webhook Signature Check',  'Validate HMAC-SHA256 signature on payment event reception',             'IN_PROGRESS','P1', 5, 1, '11111111-1111-1111-1111-111111111106', '11111111-1111-1111-1111-111111111102', '33333333-3333-3333-3333-333333333306', 1, NOW() - INTERVAL '2 days', NULL);

-- ------------------------------------------------------------------------------
-- Table 7: issue_history (10 Rows)
-- ------------------------------------------------------------------------------
INSERT INTO issue_history (issue_id, changed_by, field_name, old_value, new_value, change_reason, changed_at) VALUES
('44444444-4444-4444-4444-444444444401', '11111111-1111-1111-1111-111111111102', 'status', 'TODO',        'IN_PROGRESS', 'Beginning session pooler configuration', NOW() - INTERVAL '5 days'),
('44444444-4444-4444-4444-444444444401', '11111111-1111-1111-1111-111111111102', 'status', 'IN_PROGRESS', 'DONE',        'Validated port 5432 over SSL connection',NOW() - INTERVAL '3 days'),
('44444444-4444-4444-4444-444444444402', '11111111-1111-1111-1111-111111111102', 'status', 'TODO',        'IN_PROGRESS', 'Investigating mobile packet drop logs',  NOW() - INTERVAL '2 days'),
('44444444-4444-4444-4444-444444444404', '11111111-1111-1111-1111-111111111104', 'status', 'TODO',        'IN_PROGRESS', 'Implemented binary WebSocket protocol',   NOW() - INTERVAL '3 days'),
('44444444-4444-4444-4444-444444444404', '11111111-1111-1111-1111-111111111104', 'status', 'IN_PROGRESS', 'IN_REVIEW',   'Ready for multi-user stress testing',     NOW() - INTERVAL '1 day'),
('44444444-4444-4444-4444-444444444405', '11111111-1111-1111-1111-111111111102', 'status', 'TODO',        'IN_PROGRESS', 'Drafted PL/pgSQL function',               NOW() - INTERVAL '4 days'),
('44444444-4444-4444-4444-444444444405', '11111111-1111-1111-1111-111111111102', 'status', 'IN_PROGRESS', 'DONE',        'Verified triggers on insert/update',     NOW() - INTERVAL '2 days'),
('44444444-4444-4444-4444-444444444408', '11111111-1111-1111-1111-111111111102', 'status', 'TODO',        'IN_PROGRESS', 'Inspecting Spring Security CORS headers', NOW() - INTERVAL '6 days'),
('44444444-4444-4444-4444-444444444408', '11111111-1111-1111-1111-111111111102', 'status', 'IN_PROGRESS', 'DONE',        'Added public URL to CORS allowed origin',NOW() - INTERVAL '4 days'),
('44444444-4444-4444-4444-444444444410', '11111111-1111-1111-1111-111111111102', 'status', 'TODO',        'DONE',        'Configured OIDC trust policy in AWS IAM',NOW() - INTERVAL '3 days');

-- ------------------------------------------------------------------------------
-- Table 8: labels (10 Rows)
-- ------------------------------------------------------------------------------
INSERT INTO labels (id, project_id, name, colour) VALUES
('55555555-5555-5555-5555-555555555501', '22222222-2222-2222-2222-222222222201', 'backend',       '#3b82f6'),
('55555555-5555-5555-5555-555555555502', '22222222-2222-2222-2222-222222222201', 'frontend',      '#10b981'),
('55555555-5555-5555-5555-555555555503', '22222222-2222-2222-2222-222222222201', 'security',      '#ef4444'),
('55555555-5555-5555-5555-555555555504', '22222222-2222-2222-2222-222222222201', 'dbms',          '#8b5cf6'),
('55555555-5555-5555-5555-555555555505', '22222222-2222-2222-2222-222222222201', 'critical',      '#f97316'),
('55555555-5555-5555-5555-555555555506', '22222222-2222-2222-2222-222222222201', 'devops',        '#06b6d4'),
('55555555-5555-5555-5555-555555555507', '22222222-2222-2222-2222-222222222201', 'ai-llm',        '#ec4899'),
('55555555-5555-5555-5555-555555555508', '22222222-2222-2222-2222-222222222201', 'crdt-sync',     '#14b8a6'),
('55555555-5555-5555-5555-555555555509', '22222222-2222-2222-2222-222222222202', 'cloud-infra',   '#f59e0b'),
('55555555-5555-5555-5555-555555555510', '22222222-2222-2222-2222-222222222203', 'stripe-api',    '#6366f1');

-- ------------------------------------------------------------------------------
-- Table 9: issue_labels (12 Rows)
-- ------------------------------------------------------------------------------
INSERT INTO issue_labels (issue_id, label_id) VALUES
('44444444-4444-4444-4444-444444444401', '55555555-5555-5555-5555-555555555501'),
('44444444-4444-4444-4444-444444444401', '55555555-5555-5555-5555-555555555504'),
('44444444-4444-4444-4444-444444444402', '55555555-5555-5555-5555-555555555501'),
('44444444-4444-4444-4444-444444444402', '55555555-5555-5555-5555-555555555503'),
('44444444-4444-4444-4444-444444444402', '55555555-5555-5555-5555-555555555505'),
('44444444-4444-4444-4444-444444444403', '55555555-5555-5555-5555-555555555502'),
('44444444-4444-4444-4444-444444444404', '55555555-5555-5555-5555-555555555501'),
('44444444-4444-4444-4444-444444444404', '55555555-5555-5555-5555-555555555508'),
('44444444-4444-4444-4444-444444444406', '55555555-5555-5555-5555-555555555507'),
('44444444-4444-4444-4444-444444444408', '55555555-5555-5555-5555-555555555503'),
('44444444-4444-4444-4444-444444444409', '55555555-5555-5555-5555-555555555503'),
('44444444-4444-4444-4444-444444444410', '55555555-5555-5555-5555-555555555506');

-- ------------------------------------------------------------------------------
-- Table 10: issue_watchers (12 Rows)
-- ------------------------------------------------------------------------------
INSERT INTO issue_watchers (issue_id, user_id) VALUES
('44444444-4444-4444-4444-444444444401', '11111111-1111-1111-1111-111111111101'),
('44444444-4444-4444-4444-444444444401', '11111111-1111-1111-1111-111111111102'),
('44444444-4444-4444-4444-444444444402', '11111111-1111-1111-1111-111111111101'),
('44444444-4444-4444-4444-444444444402', '11111111-1111-1111-1111-111111111102'),
('44444444-4444-4444-4444-444444444402', '11111111-1111-1111-1111-111111111103'),
('44444444-4444-4444-4444-444444444403', '11111111-1111-1111-1111-111111111102'),
('44444444-4444-4444-4444-444444444404', '11111111-1111-1111-1111-111111111101'),
('44444444-4444-4444-4444-444444444404', '11111111-1111-1111-1111-111111111104'),
('44444444-4444-4444-4444-444444444406', '11111111-1111-1111-1111-111111111103'),
('44444444-4444-4444-4444-444444444408', '11111111-1111-1111-1111-111111111101'),
('44444444-4444-4444-4444-444444444409', '11111111-1111-1111-1111-111111111101'),
('44444444-4444-4444-4444-444444444410', '11111111-1111-1111-1111-111111111102');

-- ------------------------------------------------------------------------------
-- Table 11: comments (10 Rows, including threaded hierarchy)
-- ------------------------------------------------------------------------------
INSERT INTO comments (id, issue_id, author_id, parent_comment_id, content, created_at) VALUES
('66666666-6666-6666-6666-666666666601', '44444444-4444-4444-4444-444444444402', '11111111-1111-1111-1111-111111111101', NULL, 'Can we reproduce this timeout on mobile browsers over 4G data?', NOW() - INTERVAL '24 hours'),
('66666666-6666-6666-6666-666666666602', '44444444-4444-4444-4444-444444444402', '11111111-1111-1111-1111-111111111102', '66666666-6666-6666-6666-666666666601', 'Yes, confirmed. PKCE code exchange times out because TLS handshake latency spikes above 2500ms.', NOW() - INTERVAL '20 hours'),
('66666666-6666-6666-6666-666666666603', '44444444-4444-4444-4444-444444444402', '11111111-1111-1111-1111-111111111101', '66666666-6666-6666-6666-666666666602', 'Great catch. Let us bump the Axios client request timeout to 10 seconds in the interceptor.', NOW() - INTERVAL '16 hours'),
('66666666-6666-6666-6666-666666666604', '44444444-4444-4444-4444-444444444401', '11111111-1111-1111-1111-111111111102', NULL, 'Postgres Session pooler is now listening on 5432 with SSL enabled.', NOW() - INTERVAL '4 days'),
('66666666-6666-6666-6666-666666666605', '44444444-4444-4444-4444-444444444401', '11111111-1111-1111-1111-111111111101', '66666666-6666-6666-6666-666666666604', 'Tested Flyway migration locks; advisory locks are working reliably.', NOW() - INTERVAL '3 days'),
('66666666-6666-6666-6666-666666666606', '44444444-4444-4444-4444-444444444404', '11111111-1111-1111-1111-111111111104', NULL, 'Binary WebSocket relay has been tested with 5 concurrent active editors.', NOW() - INTERVAL '2 days'),
('66666666-6666-6666-6666-666666666607', '44444444-4444-4444-4444-444444444404', '11111111-1111-1111-1111-111111111101', '66666666-6666-6666-6666-666666666606', 'Excellent work. Memory consumption stayed under 40MB on the Caddy node.', NOW() - INTERVAL '1 day'),
('66666666-6666-6666-6666-666666666608', '44444444-4444-4444-4444-444444444406', '11111111-1111-1111-1111-111111111103', NULL, 'Can we add a toggle in project settings to enable/disable the AI duplicate check?', NOW() - INTERVAL '12 hours'),
('66666666-6666-6666-6666-666666666609', '44444444-4444-4444-4444-444444444408', '11111111-1111-1111-1111-111111111102', NULL, 'The CORS filter has been updated to accept credentials from the public sslip.io origin.', NOW() - INTERVAL '5 days'),
('66666666-6666-6666-6666-666666666610', '44444444-4444-4444-4444-444444444409', '11111111-1111-1111-1111-111111111101', NULL, 'Final security audit completed. All endpoints enforce @PreAuthorize with RBAC checks.', NOW() - INTERVAL '8 hours');

-- ------------------------------------------------------------------------------
-- Table 12: notifications (10 Rows)
-- ------------------------------------------------------------------------------
INSERT INTO notifications (recipient_id, type, title, body, referenced_type, referenced_id, project_key, is_read) VALUES
('11111111-1111-1111-1111-111111111102', 'ISSUE_ASSIGNED', 'Assigned: AD-2',    'Alice assigned you to AD-2: OAuth Callback Timeout',           'ISSUE',   '44444444-4444-4444-4444-444444444402', 'AD', TRUE),
('11111111-1111-1111-1111-111111111101', 'COMMENT_ADDED',  'New reply on AD-2',  'Bob Johnson replied to your comment on AD-2',                 'COMMENT', '66666666-6666-6666-6666-666666666602', 'AD', FALSE),
('11111111-1111-1111-1111-111111111102', 'COMMENT_ADDED',  'New reply on AD-2',  'Alice Smith replied: Let us bump the Axios timeout',          'COMMENT', '66666666-6666-6666-6666-666666666603', 'AD', FALSE),
('11111111-1111-1111-1111-111111111104', 'ISSUE_ASSIGNED', 'Assigned: AD-4',    'Alice assigned you to AD-4: Real-Time Yjs Document Collab',  'ISSUE',   '44444444-4444-4444-4444-444444444404', 'AD', TRUE),
('11111111-1111-1111-1111-111111111101', 'STATUS_CHANGED', 'AD-1 marked DONE',   'Bob completed AD-1: Configure PostgreSQL Session Pooler',     'ISSUE',   '44444444-4444-4444-4444-444444444401', 'AD', TRUE),
('11111111-1111-1111-1111-111111111101', 'STATUS_CHANGED', 'AD-5 marked DONE',   'Bob completed AD-5: Automated Database Timestamp Triggers',   'ISSUE',   '44444444-4444-4444-4444-444444444405', 'AD', TRUE),
('11111111-1111-1111-1111-111111111103', 'SPRINT_STARTED', 'Sprint 1 Active',   'Alice started Sprint 1 - Core Foundations in project AD',    'SPRINT',  '33333333-3333-3333-3333-333333333301', 'AD', FALSE),
('11111111-1111-1111-1111-111111111105', 'MEMBER_ADDED',   'Joined Project CIS', 'You have been added to Cloud Infrastructure Service as DEVELOPER', 'PROJECT', '22222222-2222-2222-2222-222222222202', 'CIS', TRUE),
('11111111-1111-1111-1111-111111111106', 'MEMBER_ADDED',   'Joined Project PAY', 'You have been added to Payment Gateway Integration as DEVELOPER', 'PROJECT', '22222222-2222-2222-2222-222222222203', 'PAY', TRUE),
('11111111-1111-1111-1111-111111111107', 'MEMBER_ADDED',   'Joined Project DATA','You have been added to Data Analytics & Warehouse as ADMIN',   'PROJECT', '22222222-2222-2222-2222-222222222209', 'DATA', FALSE);

-- ------------------------------------------------------------------------------
-- Table 13: project_docs (10 Rows)
-- ------------------------------------------------------------------------------
INSERT INTO project_docs (id, project_id, title, created_by) VALUES
('77777777-7777-7777-7777-777777777701', '22222222-2222-2222-2222-222222222201', 'Agilify System Architecture & Security Spec', '11111111-1111-1111-1111-111111111101'),
('77777777-7777-7777-7777-777777777702', '22222222-2222-2222-2222-222222222201', 'Relational Database Schema Design (14 Tables)', '11111111-1111-1111-1111-111111111101'),
('77777777-7777-7777-7777-777777777703', '22222222-2222-2222-2222-222222222201', 'CRDT Yjs WebSocket Protocol Documentation',     '11111111-1111-1111-1111-111111111104'),
('77777777-7777-7777-7777-777777777704', '22222222-2222-2222-2222-222222222201', 'Sprint 1 Sprint Planning Notes',               '11111111-1111-1111-1111-111111111102'),
('77777777-7777-7777-7777-777777777705', '22222222-2222-2222-2222-222222222202', 'AWS EC2 t3.micro Tuning & Swap File Setup',    '11111111-1111-1111-1111-111111111101'),
('77777777-7777-7777-7777-777777777706', '22222222-2222-2222-2222-222222222202', 'Caddy Reverse Proxy & HTTPS Configuration',     '11111111-1111-1111-1111-111111111101'),
('77777777-7777-7777-7777-777777777707', '22222222-2222-2222-2222-222222222203', 'Payment Webhook Idempotency Standards',       '11111111-1111-1111-1111-111111111102'),
('77777777-7777-7777-7777-777777777708', '22222222-2222-2222-2222-222222222204', 'Groq LLM Prompt Engineering & Fallbacks',       '11111111-1111-1111-1111-111111111104'),
('77777777-7777-7777-7777-777777777709', '22222222-2222-2222-2222-222222222205', 'Keycloak OIDC Client Registration Guide',       '11111111-1111-1111-1111-111111111101'),
('77777777-7777-7777-7777-777777777710', '22222222-2222-2222-2222-222222222206', 'Warehouse Barcode Scanner API Contract',        '11111111-1111-1111-1111-111111111105');

-- ------------------------------------------------------------------------------
-- Table 14: doc_snapshots (10 Rows)
-- ------------------------------------------------------------------------------
INSERT INTO doc_snapshots (doc_id, snapshot, version) VALUES
('77777777-7777-7777-7777-777777777701', decode('0102030405060708090a0b0c0d0e0f', 'hex'), 1),
('77777777-7777-7777-7777-777777777701', decode('0102030405060708090a0b0c0d0e10', 'hex'), 2),
('77777777-7777-7777-7777-777777777701', decode('0102030405060708090a0b0c0d0e11', 'hex'), 3),
('77777777-7777-7777-7777-777777777702', decode('02030405060708090a0b0c0d0e0f10', 'hex'), 1),
('77777777-7777-7777-7777-777777777703', decode('030405060708090a0b0c0d0e0f1011', 'hex'), 1),
('77777777-7777-7777-7777-777777777704', decode('0405060708090a0b0c0d0e0f101112', 'hex'), 1),
('77777777-7777-7777-7777-777777777705', decode('05060708090a0b0c0d0e0f10111213', 'hex'), 1),
('77777777-7777-7777-7777-777777777706', decode('060708090a0b0c0d0e0f1011121314', 'hex'), 1),
('77777777-7777-7777-7777-777777777707', decode('0708090a0b0c0d0e0f101112131415', 'hex'), 1),
('77777777-7777-7777-7777-777777777708', decode('08090a0b0c0d0e0f10111213141516', 'hex'), 1);


-- ==============================================================================
-- 3. VERIFICATION QUERY: ASSERT AT LEAST 10 ROWS IN ALL 14 TABLES
-- ==============================================================================
SELECT 
    table_name,
    (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I', table_name), false, true, '')))[1]::text::int AS total_rows,
    CASE 
        WHEN (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I', table_name), false, true, '')))[1]::text::int >= 10 
        THEN 'PASSED (>= 10 rows)'
        ELSE 'FAIL'
    END AS status
FROM information_schema.tables
WHERE table_schema = 'public' 
  AND table_name != 'flyway_schema_history'
ORDER BY table_name;
