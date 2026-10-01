-- Vérification du courriel : l'accès automatique d'un parent exige la preuve qu'il possède son adresse.

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
-- Comptes existants déjà actifs et validés : considérés comme vérifiés (antérieurs à la fonctionnalité).
UPDATE users SET email_verified = TRUE WHERE approved = TRUE AND password_set = TRUE AND email IS NOT NULL;

-- Jeton envoyé par courriel. purpose = VERIFY (confirmer l'adresse d'un compte créé par son titulaire)
-- ou ACTIVATE (prise en main d'un compte créé par une école : le mot de passe est choisi depuis le lien).
CREATE TABLE email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('VERIFY', 'ACTIVATE')),
    requested_school_id BIGINT REFERENCES schools(id) ON DELETE SET NULL,
    requested_role VARCHAR(30),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
