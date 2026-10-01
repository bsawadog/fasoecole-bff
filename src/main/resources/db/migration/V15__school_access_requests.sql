-- Demandes d'accès d'un enseignant, parent ou élève déjà inscrit à un établissement supplémentaire.

CREATE TABLE school_access_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    requested_role VARCHAR(30) NOT NULL CHECK (requested_role IN ('TEACHER', 'PARENT', 'STUDENT')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    decided_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_school_access_requests_pending
    ON school_access_requests (user_id, school_id, requested_role)
    WHERE status = 'PENDING';
CREATE INDEX idx_school_access_requests_school_status ON school_access_requests (school_id, status);
CREATE INDEX idx_school_access_requests_user ON school_access_requests (user_id);
