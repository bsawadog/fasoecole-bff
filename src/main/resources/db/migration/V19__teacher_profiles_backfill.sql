-- Les comptes enseignants validés (demande de compte ou d'accès) n'avaient qu'un lien school_users :
-- on crée la fiche enseignant manquante pour qu'ils puissent être affectés aux classes.
INSERT INTO teachers (user_id, school_id)
SELECT DISTINCT su.user_id, su.school_id
FROM school_users su
JOIN roles r ON r.id = su.role_id AND r.name = 'TEACHER'
WHERE NOT EXISTS (
    SELECT 1 FROM teachers t WHERE t.user_id = su.user_id AND t.school_id = su.school_id
);