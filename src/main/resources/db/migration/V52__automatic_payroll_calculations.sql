ALTER TABLE school_payables
 ADD COLUMN payroll_deductions NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK(payroll_deductions>=0),
 ADD COLUMN payroll_calculation JSONB,
 ADD COLUMN payroll_vacation_tax NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK(payroll_vacation_tax>=0),
 ADD COLUMN payroll_calculated_gross NUMERIC(12,2);

CREATE TABLE school_payroll_calculation_events (
 id BIGSERIAL PRIMARY KEY,
 school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
 employee_user_id BIGINT NOT NULL REFERENCES users(id),
 period DATE NOT NULL,
 request_id UUID NOT NULL,
 input JSONB NOT NULL,
 preview JSONB NOT NULL,
 created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
 created_at TIMESTAMP NOT NULL DEFAULT now(),
 UNIQUE(school_id,request_id)
);
CREATE INDEX idx_payroll_calculation_employee ON school_payroll_calculation_events(school_id,employee_user_id,period,id DESC);
