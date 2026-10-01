-- Désactivation d'un enseignant dans une classe : l'affectation est conservée (historique, notes, fiche)
-- mais n'est plus comptée comme active.
ALTER TABLE class_subject_teacher ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE class_subject_teacher ADD COLUMN deactivated_at TIMESTAMP;
