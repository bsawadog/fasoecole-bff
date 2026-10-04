CREATE TABLE support_incidents (
 id BIGSERIAL PRIMARY KEY, school_id BIGINT NOT NULL REFERENCES schools(id),
 reporter_id BIGINT NOT NULL REFERENCES users(id), title VARCHAR(160) NOT NULL,
 page_title VARCHAR(200) NOT NULL, description TEXT NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','IN_PROGRESS','RETEST_REQUESTED','CLOSED')),
 assigned_to BIGINT REFERENCES users(id), image BYTEA, image_type VARCHAR(30),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX support_incidents_school ON support_incidents(school_id, updated_at DESC);
CREATE TABLE support_incident_events (
 id BIGSERIAL PRIMARY KEY, incident_id BIGINT NOT NULL REFERENCES support_incidents(id),
 actor_id BIGINT NOT NULL REFERENCES users(id), action VARCHAR(30) NOT NULL,
 note TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX support_events_incident ON support_incident_events(incident_id,id);
