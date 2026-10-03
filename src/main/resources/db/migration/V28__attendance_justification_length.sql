-- Les motifs des signalements parent peuvent contenir jusqu'à 500 caractères.
ALTER TABLE attendances ALTER COLUMN justification TYPE VARCHAR(500);
