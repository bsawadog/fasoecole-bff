-- Gestion du travail des enseignants : emploi du temps hebdomadaire, pointage, heures
-- supplémentaires, taux (horaire ou mensuel) et paiements mensuels effectifs.

CREATE TABLE teacher_rates (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    rate_type VARCHAR(20) NOT NULL CHECK (rate_type IN ('HOURLY', 'MONTHLY')),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    effective_from DATE NOT NULL CHECK (EXTRACT(DAY FROM effective_from) = 1),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_teacher_rates_teacher_month UNIQUE (teacher_id, effective_from)
);

CREATE TABLE teacher_schedule_slots (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    class_id BIGINT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    day_of_week INT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_teacher_slot_times CHECK (end_time > start_time),
    CONSTRAINT ck_teacher_slot_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_teacher_schedule_slots_teacher ON teacher_schedule_slots(teacher_id);

CREATE TABLE teacher_session_records (
    id BIGSERIAL PRIMARY KEY,
    slot_id BIGINT NOT NULL REFERENCES teacher_schedule_slots(id) ON DELETE CASCADE,
    session_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('PRESENT', 'ABSENT')),
    recorded_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_teacher_session_slot_date UNIQUE (slot_id, session_date)
);

CREATE TABLE teacher_extra_hours (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    class_id BIGINT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    work_date DATE NOT NULL,
    hours NUMERIC(5,2) NOT NULL CHECK (hours > 0 AND hours <= 24),
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_teacher_extra_hours_teacher_date ON teacher_extra_hours(teacher_id, work_date);

CREATE TABLE teacher_payments (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    pay_month DATE NOT NULL CHECK (EXTRACT(DAY FROM pay_month) = 1),
    payment_date DATE NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    reference VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_teacher_payments_teacher_month ON teacher_payments(teacher_id, pay_month);