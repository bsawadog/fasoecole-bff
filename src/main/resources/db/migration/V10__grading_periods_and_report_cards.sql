-- Périodes d'évaluation (trimestres, semestres…) avec cycle de vie OPEN -> LOCKED -> PUBLISHED.
CREATE TABLE grade_periods (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    academic_year_id BIGINT NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(80) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    pass_mark NUMERIC(5,2) NOT NULL DEFAULT 10,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','LOCKED','PUBLISHED')),
    published_at TIMESTAMP,
    UNIQUE (school_id, academic_year_id, code),
    CHECK (end_date >= start_date)
);

-- Évaluations (devoir, composition…) d'une matière dans une classe, pour une période.
CREATE TABLE evaluations (
    id BIGSERIAL PRIMARY KEY,
    class_subject_teacher_id BIGINT NOT NULL REFERENCES class_subject_teacher(id) ON DELETE CASCADE,
    period_id BIGINT NOT NULL REFERENCES grade_periods(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    type VARCHAR(30) NOT NULL,
    eval_date DATE NOT NULL,
    max_value NUMERIC(5,2) NOT NULL DEFAULT 20 CHECK (max_value > 0),
    weight NUMERIC(4,2) NOT NULL DEFAULT 1 CHECK (weight > 0),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_evaluations_period ON evaluations(period_id);

ALTER TABLE grades ADD COLUMN evaluation_id BIGINT REFERENCES evaluations(id) ON DELETE CASCADE;
CREATE UNIQUE INDEX uq_grades_evaluation_student ON grades(evaluation_id, student_id) WHERE evaluation_id IS NOT NULL;

-- Traçabilité : qui a modifié quelle note, quand, et la valeur précédente.
CREATE TABLE grade_history (
    id BIGSERIAL PRIMARY KEY,
    evaluation_id BIGINT REFERENCES evaluations(id) ON DELETE SET NULL,
    evaluation_title VARCHAR(160) NOT NULL,
    class_id BIGINT REFERENCES classes(id) ON DELETE CASCADE,
    period_id BIGINT REFERENCES grade_periods(id) ON DELETE CASCADE,
    student_id BIGINT REFERENCES students(id) ON DELETE CASCADE,
    action VARCHAR(20) NOT NULL CHECK (action IN ('CREATE','UPDATE','DELETE')),
    old_value NUMERIC(5,2),
    new_value NUMERIC(5,2),
    reason VARCHAR(255),
    changed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    changed_by_name VARCHAR(160),
    changed_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_grade_history_class_period ON grade_history(class_id, period_id);

-- Bulletins : rattachement à la classe et à la période, mention et effectif.
ALTER TABLE report_cards
    ADD COLUMN class_id BIGINT REFERENCES classes(id) ON DELETE CASCADE,
    ADD COLUMN period_id BIGINT REFERENCES grade_periods(id) ON DELETE CASCADE,
    ADD COLUMN class_size INT,
    ADD COLUMN mention VARCHAR(40),
    ADD COLUMN generated_at TIMESTAMP;
CREATE UNIQUE INDEX uq_report_cards_period_student ON report_cards(period_id, student_id) WHERE period_id IS NOT NULL;
