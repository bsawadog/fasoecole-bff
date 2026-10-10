CREATE TABLE school_calendar_events (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    class_id BIGINT REFERENCES classes(id),
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('EXAM','HOLIDAY','MEETING','EVENT')),
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL CHECK (ends_on >= starts_on),
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX school_calendar_scope ON school_calendar_events(school_id, starts_on);

CREATE TABLE student_observations (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    class_id BIGINT NOT NULL REFERENCES classes(id),
    student_id BIGINT NOT NULL REFERENCES students(id),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('INCIDENT','POSITIVE')),
    observed_on DATE NOT NULL,
    description VARCHAR(2000) NOT NULL,
    action VARCHAR(2000) NOT NULL DEFAULT '',
    shared_with_family BOOLEAN NOT NULL DEFAULT FALSE,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX student_observations_scope ON student_observations(school_id, student_id, observed_on DESC);

CREATE TABLE school_document_requests (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    student_id BIGINT NOT NULL REFERENCES students(id),
    requested_by BIGINT NOT NULL REFERENCES users(id),
    kind VARCHAR(30) NOT NULL CHECK (kind IN ('SCHOOL_CERTIFICATE','ATTESTATION','OTHER')),
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','IN_PROGRESS','READY','REJECTED','CANCELLED')),
    response VARCHAR(2000) NOT NULL DEFAULT '',
    handled_by BIGINT REFERENCES users(id),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX school_document_requests_scope ON school_document_requests(school_id, student_id, created_at DESC);

CREATE TABLE library_books (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    title VARCHAR(200) NOT NULL,
    author VARCHAR(160) NOT NULL DEFAULT '',
    reference VARCHAR(80) NOT NULL,
    copies INTEGER NOT NULL CHECK (copies > 0),
    created_by BIGINT NOT NULL REFERENCES users(id),
    UNIQUE(school_id, reference),
    UNIQUE(school_id, id)
);
CREATE TABLE library_loans (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    book_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL REFERENCES students(id),
    borrowed_on DATE NOT NULL DEFAULT CURRENT_DATE,
    due_on DATE NOT NULL CHECK (due_on >= borrowed_on),
    returned_on DATE,
    issued_by BIGINT NOT NULL REFERENCES users(id),
    returned_by BIGINT REFERENCES users(id),
    FOREIGN KEY(school_id, book_id) REFERENCES library_books(school_id, id),
    CHECK (returned_on IS NULL OR returned_on >= borrowed_on)
);
CREATE INDEX library_active_loans ON library_loans(book_id) WHERE returned_on IS NULL;
CREATE INDEX library_student_loans ON library_loans(school_id, student_id);

CREATE TABLE homework_progress (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    post_id BIGINT NOT NULL REFERENCES parent_portal_posts(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES students(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('TO_DO','SUBMITTED','CORRECTED')),
    feedback VARCHAR(2000) NOT NULL DEFAULT '',
    updated_by BIGINT NOT NULL REFERENCES users(id),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(post_id, student_id)
);
CREATE INDEX homework_progress_scope ON homework_progress(school_id, student_id);

CREATE TRIGGER calendar_revision AFTER INSERT OR UPDATE OR DELETE ON school_calendar_events FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER observation_revision AFTER INSERT OR UPDATE OR DELETE ON student_observations FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER document_request_revision AFTER INSERT OR UPDATE OR DELETE ON school_document_requests FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER library_book_revision AFTER INSERT OR UPDATE OR DELETE ON library_books FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER library_loan_revision AFTER INSERT OR UPDATE OR DELETE ON library_loans FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER homework_progress_revision AFTER INSERT OR UPDATE OR DELETE ON homework_progress FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
