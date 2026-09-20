CREATE TABLE notifications (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    recipient_id    UUID         NOT NULL REFERENCES users(id),
    type            VARCHAR(50)  NOT NULL,
    title           VARCHAR(255),
    body            TEXT,
    referenced_type VARCHAR(50),
    referenced_id   UUID,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, is_read, created_at DESC);
