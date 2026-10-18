-- A delegation to a teacher is not an additional administrative salary contract.
ALTER TABLE school_staff ADD COLUMN access_only BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE school_staff ADD CONSTRAINT staff_access_only_no_salary CHECK (NOT access_only OR monthly_salary IS NULL);
