-- School records own their contact details; users retain authentication identity.
ALTER TABLE students ADD COLUMN first_name varchar(100);
ALTER TABLE students ADD COLUMN last_name varchar(100);
ALTER TABLE students ADD COLUMN email varchar(150);
ALTER TABLE students ADD COLUMN phone varchar(30);

UPDATE students s SET first_name=u.first_name, last_name=u.last_name, email=u.email, phone=u.phone
FROM users u WHERE u.id=s.user_id;

ALTER TABLE students ALTER COLUMN first_name SET NOT NULL;
ALTER TABLE students ALTER COLUMN last_name SET NOT NULL;
-- Contact emails are deliberately not unique, within or across schools.
CREATE INDEX idx_students_school_contact_email ON students(school_id, lower(email));
