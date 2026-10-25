CREATE TABLE student_study_tasks (
 id BIGSERIAL PRIMARY KEY,
 student_id BIGINT NOT NULL REFERENCES students(id),
 user_id BIGINT NOT NULL REFERENCES users(id),
 title VARCHAR(160) NOT NULL,
 details VARCHAR(2000) NOT NULL DEFAULT '',
 due_on DATE,
 done BOOLEAN NOT NULL DEFAULT false,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX student_study_tasks_owner ON student_study_tasks(user_id,student_id,done,due_on);
