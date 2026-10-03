-- Preserve existing data. Claims become parent/student links only after school approval.
CREATE TABLE user_requested_children (
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 child_index INTEGER NOT NULL,
 registration_number VARCHAR(50) NOT NULL,
 PRIMARY KEY (user_id, child_index)
);
CREATE TABLE school_access_requested_children (
 request_id BIGINT NOT NULL REFERENCES school_access_requests(id) ON DELETE CASCADE,
 child_index INTEGER NOT NULL,
 registration_number VARCHAR(50) NOT NULL,
 PRIMARY KEY (request_id, child_index)
);
CREATE TABLE verification_requested_children (
 token_id BIGINT NOT NULL REFERENCES email_verification_tokens(id) ON DELETE CASCADE,
 child_index INTEGER NOT NULL,
 registration_number VARCHAR(50) NOT NULL,
 PRIMARY KEY (token_id, child_index)
);
