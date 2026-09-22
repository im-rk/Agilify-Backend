CREATE TABLE project_docs (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id  UUID         NOT NULL REFERENCES projects(id),
    title       VARCHAR(500) NOT NULL DEFAULT 'Untitled',
    created_by  UUID         NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_docs_project ON project_docs (project_id) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_docs_updated_at BEFORE UPDATE ON project_docs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Not really a single "snapshot": each row is one binary Yjs update in the log,
-- ordered by version. See DocSnapshotService for why (server can't merge Yjs binary in Java).
CREATE TABLE doc_snapshots (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    doc_id      UUID        NOT NULL REFERENCES project_docs(id) ON DELETE CASCADE,
    snapshot    BYTEA       NOT NULL,
    version     BIGINT      NOT NULL,
    saved_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_snapshots_doc_version ON doc_snapshots (doc_id, version DESC);
