ALTER TABLE users
    ADD COLUMN requested_school_id BIGINT REFERENCES schools(id);

CREATE INDEX idx_users_pending_school
    ON users (requested_school_id, created_at)
    WHERE approved = FALSE;
