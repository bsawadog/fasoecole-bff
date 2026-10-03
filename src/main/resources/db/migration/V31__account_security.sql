ALTER TABLE users ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
-- Les anciens liens sont conservés, mais restent sans adresse liée : leur destinataire
-- au moment de l'envoi ne peut pas être établi. Le titulaire doit demander un nouveau lien.
ALTER TABLE password_reset_tokens ADD COLUMN recipient_email VARCHAR(150);
ALTER TABLE email_verification_tokens ADD COLUMN recipient_email VARCHAR(150);
ALTER TABLE email_verification_tokens ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
