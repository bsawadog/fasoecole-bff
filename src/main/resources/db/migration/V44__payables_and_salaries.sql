ALTER TABLE school_staff ADD COLUMN monthly_salary NUMERIC(12,2) CHECK (monthly_salary >= 0);
ALTER TABLE teachers ADD COLUMN monthly_salary NUMERIC(12,2) CHECK (monthly_salary >= 0);
ALTER TABLE expenses ADD COLUMN managed_payment BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE teacher_payments ADD COLUMN managed_payment BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE teacher_rates DROP CONSTRAINT teacher_rates_rate_type_check;
ALTER TABLE teacher_rates ADD CONSTRAINT teacher_rates_rate_type_check CHECK (rate_type IN ('HOURLY','MONTHLY','FIXED_MONTHLY'));
UPDATE expense_categories SET system_code='STAFF_PAYROLL' WHERE system_code IS NULL AND lower(name)='salaires du personnel';

CREATE TABLE fixed_school_charges (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    category_id BIGINT NOT NULL REFERENCES expense_categories(id),
    label VARCHAR(150) NOT NULL,
    supplier VARCHAR(150),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    due_day INTEGER NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    start_month DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE TABLE school_payables (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    category_id BIGINT NOT NULL REFERENCES expense_categories(id),
    source VARCHAR(30) NOT NULL CHECK (source IN ('MANUAL','FIXED','STAFF_SALARY','TEACHER_SALARY')),
    source_key VARCHAR(100) NOT NULL,
    period DATE NOT NULL,
    due_date DATE NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    label VARCHAR(150) NOT NULL,
    supplier VARCHAR(150),
    notes VARCHAR(500),
    teacher_id BIGINT REFERENCES teachers(id),
    cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (school_id, source_key, period)
);
CREATE INDEX idx_school_payables_due ON school_payables(school_id, due_date);
CREATE TABLE school_payable_payments (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    payable_id BIGINT NOT NULL REFERENCES school_payables(id),
    request_id UUID NOT NULL UNIQUE,
    payment_date DATE NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    method VARCHAR(30) NOT NULL,
    reference VARCHAR(100),
    expense_id BIGINT UNIQUE REFERENCES expenses(id),
    teacher_payment_id BIGINT UNIQUE REFERENCES teacher_payments(id),
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CHECK ((expense_id IS NOT NULL) <> (teacher_payment_id IS NOT NULL))
);
CREATE INDEX idx_payable_payments_payable ON school_payable_payments(payable_id);
CREATE TRIGGER payable_revision AFTER INSERT OR UPDATE OR DELETE ON school_payables
    FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER payable_payment_revision AFTER INSERT OR UPDATE OR DELETE ON school_payable_payments
    FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
CREATE TRIGGER fixed_charge_revision AFTER INSERT OR UPDATE OR DELETE ON fixed_school_charges
    FOR EACH ROW EXECUTE FUNCTION bump_school_data_revision();
