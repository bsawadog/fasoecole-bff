ALTER TABLE users ADD COLUMN school_identifier VARCHAR(50);
ALTER TABLE school_access_requests ADD COLUMN school_identifier VARCHAR(50);
ALTER TABLE email_verification_tokens ADD COLUMN school_identifier VARCHAR(50);
ALTER TABLE teachers ADD COLUMN employee_number VARCHAR(50);
UPDATE teachers SET employee_number = 'EMP-' || id;
ALTER TABLE teachers ALTER COLUMN employee_number SET NOT NULL;
ALTER TABLE teachers ADD CONSTRAINT uk_teacher_school_employee_number UNIQUE (school_id, employee_number);
