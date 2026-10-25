ALTER TABLE school_payables
 ADD COLUMN payroll_iuts NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (payroll_iuts >= 0),
 ADD COLUMN payroll_tpa NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (payroll_tpa >= 0),
 ADD COLUMN payroll_tax_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE school_payroll_tax_events (
 id BIGSERIAL PRIMARY KEY,
 school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
 payable_id BIGINT NOT NULL REFERENCES school_payables(id) ON DELETE CASCADE,
 iuts NUMERIC(12,2) NOT NULL,
 tpa NUMERIC(12,2) NOT NULL,
 version BIGINT NOT NULL,
 reason VARCHAR(300) NOT NULL,
 created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
 created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_payroll_tax_events_school ON school_payroll_tax_events(school_id,payable_id,id DESC);
