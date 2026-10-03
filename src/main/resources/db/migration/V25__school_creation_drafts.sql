ALTER TABLE schools DROP CONSTRAINT IF EXISTS schools_status_check;
ALTER TABLE schools ADD CONSTRAINT schools_status_check
    CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'ARCHIVED'));
