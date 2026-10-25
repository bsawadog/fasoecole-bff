-- School email snapshots from V60 are retained for historical preservation only.
-- The operational personal email is exclusively users.email.
ALTER TABLE email_verification_tokens ADD COLUMN previous_email varchar(150);
ALTER TABLE email_verification_tokens ADD COLUMN request_session_version bigint;
ALTER TABLE email_verification_tokens DROP CONSTRAINT email_verification_tokens_purpose_check;
ALTER TABLE email_verification_tokens ADD CONSTRAINT email_verification_tokens_purpose_check
    CHECK (purpose IN ('VERIFY', 'ACTIVATE', 'CHANGE_EMAIL'));
