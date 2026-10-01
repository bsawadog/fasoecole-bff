-- Inscriptions, réinscriptions et passage d'année.
-- Une inscription d'une année clôturée passe au statut COMPLETED et garde la décision de fin d'année.

ALTER TABLE student_enrollments DROP CONSTRAINT IF EXISTS student_enrollments_status_check;
ALTER TABLE student_enrollments ADD CONSTRAINT student_enrollments_status_check
    CHECK (status IN ('ACTIVE', 'COMPLETED', 'TRANSFERRED', 'GRADUATED', 'DROPPED'));

ALTER TABLE student_enrollments
    ADD COLUMN decision VARCHAR(20)
        CONSTRAINT student_enrollments_decision_check CHECK (decision IN ('PROMOTED', 'REPEATED', 'GRADUATED', 'LEFT')),
    ADD COLUMN decision_average NUMERIC(5,2),
    ADD COLUMN decided_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_student_enrollments_year ON student_enrollments(academic_year_id);
CREATE INDEX IF NOT EXISTS idx_student_enrollments_student ON student_enrollments(student_id);

-- Nouveau module délégable au personnel.
ALTER TABLE school_staff_modules DROP CONSTRAINT IF EXISTS school_staff_modules_module_check;
ALTER TABLE school_staff_modules ADD CONSTRAINT school_staff_modules_module_check CHECK (module IN
    ('DASHBOARD','MANAGEMENT','STUDENTS','TEACHERS','FINANCE','EXPENSES','GRADES','ENROLLMENT'));
