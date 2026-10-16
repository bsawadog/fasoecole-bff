CREATE TABLE teacher_subjects (
    teacher_id BIGINT NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    PRIMARY KEY (teacher_id, subject_id)
);
CREATE INDEX idx_teacher_subjects_subject ON teacher_subjects(subject_id);

INSERT INTO teacher_subjects(teacher_id, subject_id)
SELECT DISTINCT cst.teacher_id, cst.subject_id
FROM class_subject_teacher cst
JOIN teachers t ON t.id = cst.teacher_id
JOIN subjects s ON s.id = cst.subject_id AND s.school_id = t.school_id;
