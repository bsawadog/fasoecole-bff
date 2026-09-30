ALTER TABLE users
    ADD COLUMN requested_role VARCHAR(30);

ALTER TABLE users
    ADD CONSTRAINT chk_users_requested_role
        CHECK (requested_role IS NULL OR requested_role IN ('TEACHER', 'PARENT', 'STUDENT'));
