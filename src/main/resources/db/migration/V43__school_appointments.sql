CREATE TABLE school_appointments (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    organizer_user_id BIGINT NOT NULL REFERENCES users(id),
    recipient_user_id BIGINT NOT NULL REFERENCES users(id),
    proposed_at TIMESTAMP NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED')),
    response VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (organizer_user_id <> recipient_user_id)
);
CREATE INDEX idx_school_appointments_school ON school_appointments(school_id, proposed_at);
CREATE INDEX idx_school_appointments_recipient ON school_appointments(recipient_user_id, proposed_at);
CREATE TRIGGER school_appointment_revision AFTER INSERT OR UPDATE OR DELETE ON school_appointments
    FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
