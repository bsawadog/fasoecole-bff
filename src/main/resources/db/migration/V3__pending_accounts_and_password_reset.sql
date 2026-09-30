ALTER TABLE users
    ADD COLUMN approved BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_users_pending_approval
    ON users (approved, created_at)
    WHERE approved = FALSE;

CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
