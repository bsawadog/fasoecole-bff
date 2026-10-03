ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE user_platform_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL CHECK (role = 'SUPER_ADMIN'),
    PRIMARY KEY (user_id, role)
);
ALTER TABLE schools ADD COLUMN activated_at TIMESTAMP;
ALTER TABLE schools ADD COLUMN deactivated_at TIMESTAMP;
-- Legacy active schools use their creation date; future changes record the actual time.
UPDATE schools SET activated_at = created_at WHERE status = 'ACTIVE';
CREATE TABLE school_status_events (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    actor_id BIGINT NOT NULL REFERENCES users(id),
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX school_status_events_school_idx ON school_status_events(school_id, changed_at);
