CREATE TABLE conversation_attachments (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES school_conversation_messages(id) ON DELETE CASCADE,
    filename VARCHAR(200) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760)
);
CREATE INDEX idx_conversation_attachments_message ON conversation_attachments(message_id);
CREATE TABLE conversation_attachment_content (
    id BIGINT PRIMARY KEY REFERENCES conversation_attachments(id) ON DELETE CASCADE,
    data BYTEA NOT NULL
);
