-- ==============================================================================
-- AGILIFY — DATABASE SCHEMA DEFINITION SCRIPT (DDL)
-- ==============================================================================
-- Course: 22AIE303 Database Management Systems
-- Project: Agilify (Agile Project Management Platform)
-- Database Engine: PostgreSQL 15+
-- Description: Complete SQL DDL script defining all 14 tables, domain constraints,
--              foreign keys, composite keys, partial unique indexes, and triggers.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- EXTENSIONS & GLOBAL UTILITIES
-- ------------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

-- Trigger function for automated updated_at timestamping
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ------------------------------------------------------------------------------
-- 1. USERS TABLE
-- ------------------------------------------------------------------------------
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

-- Partial unique index ensuring uniqueness of active emails while allowing soft-deletes
CREATE UNIQUE INDEX idx_users_email_active ON users (email) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ------------------------------------------------------------------------------
-- 2. PROJECTS TABLE
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 3. PROJECT_MEMBERS TABLE (User-Project Association with Per-Project RBAC)
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 4. WORKFLOW_CONFIG TABLE (Configurable Status Transition State Machine)
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 5. SPRINTS TABLE (Iterative Planning Cycles)
-- ------------------------------------------------------------------------------
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

-- Partial Unique Index enforcing the single-active sprint invariant per project
CREATE UNIQUE INDEX idx_sprints_one_active_per_project ON sprints (project_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_sprints_project ON sprints (project_id, status);
CREATE TRIGGER trg_sprints_updated_at BEFORE UPDATE ON sprints FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ------------------------------------------------------------------------------
-- 6. ISSUES TABLE (Core Work Items: Bugs, Stories, Tasks, Epics)
-- ------------------------------------------------------------------------------
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

-- Trigger Function: Automatic issue lifecycle timestamp tracking
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

CREATE TRIGGER trg_issues_timestamps BEFORE UPDATE ON issues FOR EACH ROW EXECUTE FUNCTION set_issue_timestamps();
CREATE TRIGGER trg_issues_updated_at BEFORE UPDATE ON issues FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ------------------------------------------------------------------------------
-- 7. ISSUE_HISTORY TABLE (Append-Only Audit Trail)
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 8. LABELS TABLE (Per-Project Categorization Tags)
-- ------------------------------------------------------------------------------
CREATE TABLE labels (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    colour          VARCHAR(7)   NOT NULL DEFAULT '#888888',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT labels_unique UNIQUE (project_id, name)
);

CREATE INDEX idx_labels_project ON labels (project_id);

-- ------------------------------------------------------------------------------
-- 9. ISSUE_LABELS TABLE (Many-to-Many Association with Composite PK)
-- ------------------------------------------------------------------------------
CREATE TABLE issue_labels (
    issue_id        UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    label_id        UUID         NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    added_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (issue_id, label_id)
);

CREATE INDEX idx_issue_labels_label ON issue_labels (label_id);

-- ------------------------------------------------------------------------------
-- 10. ISSUE_WATCHERS TABLE (Notification Subscriptions with Composite PK)
-- ------------------------------------------------------------------------------
CREATE TABLE issue_watchers (
    issue_id        UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    added_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (issue_id, user_id)
);

CREATE INDEX idx_issue_watchers_user ON issue_watchers (user_id);

-- ------------------------------------------------------------------------------
-- 11. COMMENTS TABLE (Threaded Hierarchical Discussions)
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 12. NOTIFICATIONS TABLE (User Activity Inbox)
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 13. PROJECT_DOCS TABLE (Collaborative Document Metadata)
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 14. DOC_SNAPSHOTS TABLE (Append-Only Yjs CRDT Binary Vector Store)
-- ------------------------------------------------------------------------------
CREATE TABLE doc_snapshots (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    doc_id          UUID         NOT NULL REFERENCES project_docs(id) ON DELETE CASCADE,
    snapshot        BYTEA        NOT NULL,
    version         BIGINT       NOT NULL,
    saved_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT doc_snapshots_version_unique UNIQUE (doc_id, version)
);

CREATE INDEX idx_doc_snapshots_doc ON doc_snapshots (doc_id, version ASC);
