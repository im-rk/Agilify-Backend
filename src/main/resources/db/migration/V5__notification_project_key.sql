-- Lets the frontend deep-link a notification straight to /projects/{key}/issues/{id}
-- without a second lookup — the referenced issue's project didn't travel with it before.
ALTER TABLE notifications ADD COLUMN project_key VARCHAR(10);
