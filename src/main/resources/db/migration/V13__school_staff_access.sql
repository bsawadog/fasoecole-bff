-- Personnel administratif (directeur, comptable, secrétaire...) avec accès délégué par module.

INSERT INTO roles (name) SELECT 'STAFF' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'STAFF');

CREATE TABLE school_staff (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_title VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    CONSTRAINT uk_school_staff_school_user UNIQUE (school_id, user_id)
);
CREATE INDEX idx_school_staff_user ON school_staff(user_id);

CREATE TABLE school_staff_modules (
    staff_id BIGINT NOT NULL REFERENCES school_staff(id) ON DELETE CASCADE,
    module VARCHAR(30) NOT NULL CHECK (module IN
        ('DASHBOARD','MANAGEMENT','STUDENTS','TEACHERS','FINANCE','EXPENSES','GRADES')),
    PRIMARY KEY (staff_id, module)
);
