-- Comptes parents validés (demande de compte ou d'accès) sans fiche parent : on la crée pour qu'ils puissent
-- être retrouvés et rattachés à un élève lors d'une inscription.
INSERT INTO parents (user_id)
SELECT DISTINCT su.user_id
FROM school_users su
JOIN roles r ON r.id = su.role_id AND r.name = 'PARENT'
WHERE NOT EXISTS (SELECT 1 FROM parents p WHERE p.user_id = su.user_id);