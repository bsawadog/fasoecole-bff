-- Parents / tuteurs sans adresse courriel : seuls le nom et le prénom sont obligatoires.
-- Ces comptes ne peuvent pas se connecter tant qu'aucun courriel n'est renseigné.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
