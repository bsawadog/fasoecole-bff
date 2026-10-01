-- Accès automatique des parents aux établissements où leurs enfants sont inscrits.

-- Comptes créés par l'école (ex. parent saisi à l'inscription) : mot de passe aléatoire non connu du parent.
ALTER TABLE users ADD COLUMN password_set BOOLEAN NOT NULL DEFAULT TRUE;

-- AUTO_APPROVED : accès accordé par le système (parent reconnu par son courriel) ;
-- REVOKED : accès retiré par le propriétaire, plus jamais réaccordé automatiquement.
ALTER TABLE school_access_requests DROP CONSTRAINT IF EXISTS school_access_requests_status_check;
ALTER TABLE school_access_requests ADD CONSTRAINT school_access_requests_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'AUTO_APPROVED', 'REVOKED'));
