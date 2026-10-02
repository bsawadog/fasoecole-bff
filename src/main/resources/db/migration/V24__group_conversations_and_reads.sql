ALTER TABLE school_conversations ALTER COLUMN parent_user_id DROP NOT NULL;

CREATE TABLE conversation_participants (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES school_conversations(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    school_id BIGINT REFERENCES schools(id) ON DELETE CASCADE,
    last_read_at TIMESTAMP,
    CONSTRAINT chk_conversation_participant_target CHECK ((user_id IS NOT NULL)::int + (school_id IS NOT NULL)::int = 1)
);
CREATE UNIQUE INDEX uq_conversation_user_participant ON conversation_participants(conversation_id, user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_conversation_school_participant ON conversation_participants(conversation_id, school_id) WHERE school_id IS NOT NULL;
CREATE INDEX idx_conversation_participants_user ON conversation_participants(user_id, conversation_id);
CREATE INDEX idx_conversation_participants_school ON conversation_participants(school_id, conversation_id);

-- Existing family conversations become a two-party parent / school exchange.
INSERT INTO conversation_participants(conversation_id, user_id, last_read_at)
SELECT id, parent_user_id, CASE WHEN unread_by_parent THEN NULL ELSE last_message_at END
FROM school_conversations WHERE parent_user_id IS NOT NULL;
INSERT INTO conversation_participants(conversation_id, school_id)
SELECT id, school_id FROM school_conversations;
UPDATE conversation_participants p SET last_read_at = c.last_message_at
FROM school_conversations c WHERE p.conversation_id = c.id AND p.school_id = c.school_id
  AND NOT c.unread_by_school;
