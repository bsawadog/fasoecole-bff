ALTER TABLE schools ADD COLUMN expected_student_count INTEGER;
ALTER TABLE schools ADD COLUMN expected_class_count INTEGER;
ALTER TABLE schools ADD COLUMN expected_teacher_count INTEGER;
ALTER TABLE schools ADD CONSTRAINT schools_expected_counts_check CHECK (
    (expected_student_count IS NULL OR expected_student_count >= 0)
    AND (expected_class_count IS NULL OR expected_class_count >= 1)
    AND (expected_teacher_count IS NULL OR expected_teacher_count >= 0)
);
