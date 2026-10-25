CREATE TABLE account_recovery_requests (
 id bigserial PRIMARY KEY,
 school_id bigint NOT NULL REFERENCES schools(id),
 student_id bigint REFERENCES students(id) ON DELETE SET NULL,
 user_id bigint NOT NULL REFERENCES users(id),
 first_name varchar(100) NOT NULL, last_name varchar(100) NOT NULL,
 registration_number varchar(50) NOT NULL, new_email varchar(150) NOT NULL,
 reason varchar(1000) NOT NULL,
 status varchar(30) NOT NULL CHECK(status IN ('PENDING','SUPPORT_REQUIRED','APPROVED','REJECTED','COMPLETED','SUPERSEDED')),
 created_at timestamp NOT NULL, decided_at timestamp, completed_at timestamp,
 decided_by bigint REFERENCES users(id), support_decision boolean NOT NULL DEFAULT false,
 verification_note varchar(1000), delivery_status varchar(20), old_notification_status varchar(20),
 token_hash varchar(64) UNIQUE, expires_at timestamp,
 previous_email varchar(150), request_session_version bigint,
 last_sent_at timestamp, last_sent_by bigint REFERENCES users(id), send_count integer NOT NULL DEFAULT 0
);
CREATE INDEX idx_recovery_school_created ON account_recovery_requests(school_id,created_at DESC);
CREATE INDEX idx_recovery_user_status ON account_recovery_requests(user_id,status);
CREATE TABLE account_recovery_events (
 id bigserial PRIMARY KEY, request_id bigint NOT NULL REFERENCES account_recovery_requests(id),
 actor_id bigint REFERENCES users(id), action varchar(40) NOT NULL,
 details varchar(1000), created_at timestamp NOT NULL
);
CREATE INDEX idx_recovery_event_request ON account_recovery_events(request_id,created_at);
