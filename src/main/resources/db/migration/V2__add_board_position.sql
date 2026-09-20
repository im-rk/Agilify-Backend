ALTER TABLE issues ADD COLUMN board_position INTEGER;

CREATE INDEX idx_issues_board ON issues (sprint_id, status, board_position) WHERE deleted_at IS NULL;
