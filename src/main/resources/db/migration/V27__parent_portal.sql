CREATE TABLE parent_portal_posts (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    class_id BIGINT REFERENCES classes(id),
    student_id BIGINT REFERENCES students(id),
    author_id BIGINT NOT NULL REFERENCES users(id),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('ANNOUNCEMENT', 'HOMEWORK', 'DOCUMENT')),
    title VARCHAR(160) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    due_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_parent_portal_posts_school ON parent_portal_posts(school_id, created_at DESC);
CREATE TABLE parent_portal_files (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES parent_portal_posts(id) ON DELETE CASCADE,
    filename VARCHAR(200) NOT NULL,
    size_bytes BIGINT NOT NULL,
    data BYTEA NOT NULL
);
CREATE INDEX idx_parent_portal_files_post ON parent_portal_files(post_id);
CREATE TABLE parent_appointments (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    student_id BIGINT NOT NULL REFERENCES students(id),
    parent_user_id BIGINT NOT NULL REFERENCES users(id),
    teacher_user_id BIGINT REFERENCES users(id),
    proposed_at TIMESTAMP NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    response VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_parent_appointments_school ON parent_appointments(school_id, proposed_at);
CREATE INDEX idx_parent_appointments_parent ON parent_appointments(parent_user_id, student_id);
