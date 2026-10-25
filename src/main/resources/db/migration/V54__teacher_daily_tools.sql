CREATE TABLE teacher_lesson_logs (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
 class_id BIGINT NOT NULL REFERENCES classes(id), subject_id BIGINT NOT NULL REFERENCES subjects(id),
 lesson_on DATE NOT NULL, title VARCHAR(160) NOT NULL, content VARCHAR(4000) NOT NULL,
 objectives VARCHAR(2000) NOT NULL DEFAULT '', next_work VARCHAR(2000) NOT NULL DEFAULT '', shared BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX teacher_lessons_scope ON teacher_lesson_logs(user_id,class_id,lesson_on);
CREATE TABLE teacher_private_notes (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id), title VARCHAR(160) NOT NULL,
 content VARCHAR(4000) NOT NULL, remind_on DATE, done BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX teacher_notes_scope ON teacher_private_notes(user_id,remind_on);
CREATE TABLE teacher_student_support (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id), class_id BIGINT NOT NULL REFERENCES classes(id),
 student_id BIGINT NOT NULL REFERENCES students(id), action VARCHAR(4000) NOT NULL,
 review_on DATE, resolved BOOLEAN NOT NULL DEFAULT false,
 family_message VARCHAR(2000) NOT NULL DEFAULT '', shared BOOLEAN NOT NULL DEFAULT false,
 UNIQUE(user_id,class_id,student_id), CHECK (NOT shared OR length(trim(family_message))>0)
);
CREATE TABLE teacher_availability_requests (
 id BIGSERIAL PRIMARY KEY, teacher_id BIGINT NOT NULL REFERENCES teachers(id),
 starts_at TIMESTAMP NOT NULL, ends_at TIMESTAMP NOT NULL, reason VARCHAR(2000) NOT NULL,
 replacement_requested BOOLEAN NOT NULL, conversation_id BIGINT NOT NULL REFERENCES school_conversations(id),
 CHECK(ends_at>starts_at)
);
