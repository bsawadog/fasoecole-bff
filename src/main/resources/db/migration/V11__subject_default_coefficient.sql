-- Coefficient par défaut porté par la matière ; class_subject_teacher.coefficient devient une
-- surcharge facultative pour une classe (NULL = coefficient de la matière).

ALTER TABLE subjects ADD COLUMN coefficient NUMERIC(4,2) NOT NULL DEFAULT 1;
ALTER TABLE subjects ADD CONSTRAINT chk_subjects_coefficient CHECK (coefficient >= 0.25 AND coefficient <= 20);

-- Coefficient par défaut = valeur la plus utilisée par la matière dans les classes (la plus élevée en cas d'égalité).
UPDATE subjects s
SET coefficient = src.coefficient
FROM (
    SELECT DISTINCT ON (subject_id) subject_id, coefficient
    FROM class_subject_teacher
    WHERE coefficient IS NOT NULL
    GROUP BY subject_id, coefficient
    ORDER BY subject_id, COUNT(*) DESC, coefficient DESC
) src
WHERE src.subject_id = s.id;

ALTER TABLE class_subject_teacher ALTER COLUMN coefficient DROP DEFAULT;
ALTER TABLE class_subject_teacher ALTER COLUMN coefficient DROP NOT NULL;

-- Les affectations identiques au coefficient de la matière héritent désormais de celui-ci.
UPDATE class_subject_teacher cst
SET coefficient = NULL
FROM subjects s
WHERE s.id = cst.subject_id AND cst.coefficient = s.coefficient;
