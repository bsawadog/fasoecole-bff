-- Espace parent : signalement d'absences et échanges avec l'établissement.

CREATE TABLE absence_reports (
    id             BIGSERIAL PRIMARY KEY,
    student_id     BIGINT       NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    school_id      BIGINT       NOT NULL REFERENCES schools (id) ON DELETE CASCADE,
    reported_by    BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    school_comment VARCHAR(500),
    handled_by     BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    handled_at     TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_absence_reports_status CHECK (status IN ('PENDING', 'ACKNOWLEDGED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_absence_reports_dates CHECK (end_date >= start_date)
);

CREATE INDEX idx_absence_reports_school ON absence_reports (school_id, status, created_at DESC);
CREATE INDEX idx_absence_reports_student ON absence_reports (student_id, start_date);

CREATE TABLE school_conversations (
    id               BIGSERIAL PRIMARY KEY,
    school_id        BIGINT       NOT NULL REFERENCES schools (id) ON DELETE CASCADE,
    parent_user_id   BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    student_id       BIGINT       REFERENCES students (id) ON DELETE SET NULL,
    subject          VARCHAR(160) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unread_by_school BOOLEAN      NOT NULL DEFAULT TRUE,
    unread_by_parent BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_school_conversations_school ON school_conversations (school_id, last_message_at DESC);
CREATE INDEX idx_school_conversations_parent ON school_conversations (parent_user_id, last_message_at DESC);

CREATE TABLE school_conversation_messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT    NOT NULL REFERENCES school_conversations (id) ON DELETE CASCADE,
    sender_id       BIGINT    REFERENCES users (id) ON DELETE SET NULL,
    from_school     BOOLEAN   NOT NULL,
    content         TEXT      NOT NULL,
    sent_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_school_conversation_messages ON school_conversation_messages (conversation_id, sent_at);
