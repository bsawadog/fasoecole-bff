-- =========================================================
-- V1__init_schema.sql
-- Schéma initial FasoÉcole
-- =========================================================

-- ================== ROLES & UTILISATEURS ==================

CREATE TABLE roles (
                       id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       first_name VARCHAR(100) NOT NULL,
                       last_name VARCHAR(100) NOT NULL,
                       email VARCHAR(150) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       phone VARCHAR(30),
                       active BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMP NOT NULL DEFAULT now(),
                       updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ================== ÉCOLES ==================

CREATE TABLE schools (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(200) NOT NULL,
                         type VARCHAR(30) NOT NULL CHECK (type IN ('PRIMAIRE','SECONDAIRE','UNIVERSITE','FORMATION')),
                         address VARCHAR(255),
                         phone VARCHAR(30),
                         email VARCHAR(150),
                         owner_id BIGINT NOT NULL REFERENCES users(id),
                         status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED')),
                         created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE school_users (
                              id BIGSERIAL PRIMARY KEY,
                              user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                              school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
                              role_id BIGINT NOT NULL REFERENCES roles(id),
                              created_at TIMESTAMP NOT NULL DEFAULT now(),
                              UNIQUE (user_id, school_id, role_id)
);

-- ================== STRUCTURE ACADÉMIQUE ==================

CREATE TABLE academic_years (
                                id BIGSERIAL PRIMARY KEY,
                                school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
                                label VARCHAR(20) NOT NULL,
                                start_date DATE NOT NULL,
                                end_date DATE NOT NULL,
                                is_current BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE levels (
                        id BIGSERIAL PRIMARY KEY,
                        school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
                        name VARCHAR(100) NOT NULL,
                        cycle VARCHAR(30) NOT NULL,
                        order_index INT NOT NULL DEFAULT 0
);

CREATE TABLE classes (
                         id BIGSERIAL PRIMARY KEY,
                         school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
                         academic_year_id BIGINT NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
                         level_id BIGINT NOT NULL REFERENCES levels(id),
                         name VARCHAR(100) NOT NULL,
                         capacity INT
);

CREATE TABLE subjects (
                          id BIGSERIAL PRIMARY KEY,
                          school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
                          name VARCHAR(150) NOT NULL,
                          code VARCHAR(30)
);

-- ================== ÉLÈVES, PARENTS, ENSEIGNANTS ==================

CREATE TABLE teachers (
                          id BIGSERIAL PRIMARY KEY,
                          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
                          specialty VARCHAR(150),
                          hire_date DATE
);

CREATE TABLE class_subject_teacher (
                                       id BIGSERIAL PRIMARY KEY,
                                       class_id BIGINT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
                                       subject_id BIGINT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
                                       teacher_id BIGINT NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
                                       coefficient NUMERIC(4,2) NOT NULL DEFAULT 1,
                                       UNIQUE (class_id, subject_id, teacher_id)
);

CREATE TABLE students (
                          id BIGSERIAL PRIMARY KEY,
                          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
                          registration_number VARCHAR(50) NOT NULL,
                          birth_date DATE,
                          gender VARCHAR(10),
                          UNIQUE (school_id, registration_number)
);

CREATE TABLE student_enrollments (
                                     id BIGSERIAL PRIMARY KEY,
                                     student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                                     class_id BIGINT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
                                     academic_year_id BIGINT NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
                                     status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','TRANSFERRED','GRADUATED','DROPPED')),
                                     enrollment_date DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE parents (
                         id BIGSERIAL PRIMARY KEY,
                         user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE parent_student (
                                id BIGSERIAL PRIMARY KEY,
                                parent_id BIGINT NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
                                student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                                relationship VARCHAR(50),
                                UNIQUE (parent_id, student_id)
);

-- ================== PÉDAGOGIE ==================

CREATE TABLE grades (
                        id BIGSERIAL PRIMARY KEY,
                        student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                        class_subject_teacher_id BIGINT NOT NULL REFERENCES class_subject_teacher(id) ON DELETE CASCADE,
                        term VARCHAR(20) NOT NULL,
                        type VARCHAR(30) NOT NULL,
                        value NUMERIC(5,2) NOT NULL,
                        max_value NUMERIC(5,2) NOT NULL DEFAULT 20,
                        grade_date DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE attendances (
                             id BIGSERIAL PRIMARY KEY,
                             student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                             class_id BIGINT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
                             attendance_date DATE NOT NULL,
                             status VARCHAR(20) NOT NULL CHECK (status IN ('PRESENT','ABSENT','LATE','EXCUSED')),
                             justification VARCHAR(255)
);

CREATE TABLE report_cards (
                              id BIGSERIAL PRIMARY KEY,
                              student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                              academic_year_id BIGINT NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
                              term VARCHAR(20) NOT NULL,
                              average NUMERIC(5,2),
                              rank INT,
                              comment VARCHAR(255),
                              validated BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE documents (
                           id BIGSERIAL PRIMARY KEY,
                           school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
                           class_id BIGINT REFERENCES classes(id) ON DELETE SET NULL,
                           uploaded_by BIGINT NOT NULL REFERENCES users(id),
                           title VARCHAR(200) NOT NULL,
                           file_url VARCHAR(500) NOT NULL,
                           type VARCHAR(50),
                           created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ================== FINANCES ==================

CREATE TABLE fee_types (
                           id BIGSERIAL PRIMARY KEY,
                           school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
                           name VARCHAR(150) NOT NULL,
                           amount NUMERIC(12,2) NOT NULL,
                           frequency VARCHAR(30) NOT NULL DEFAULT 'ONE_TIME' CHECK (frequency IN ('ONE_TIME','MONTHLY','TERM','YEARLY'))
);

CREATE TABLE invoices (
                          id BIGSERIAL PRIMARY KEY,
                          student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                          fee_type_id BIGINT NOT NULL REFERENCES fee_types(id),
                          academic_year_id BIGINT NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
                          amount_due NUMERIC(12,2) NOT NULL,
                          due_date DATE NOT NULL,
                          status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID','OVERDUE','CANCELLED'))
);

CREATE TABLE payments (
                          id BIGSERIAL PRIMARY KEY,
                          invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
                          amount NUMERIC(12,2) NOT NULL,
                          payment_date DATE NOT NULL DEFAULT CURRENT_DATE,
                          method VARCHAR(30) NOT NULL CHECK (method IN ('CASH','MOBILE_MONEY','BANK_TRANSFER','CARD')),
                          reference VARCHAR(100)
);

-- ================== COMMUNICATION & PLATEFORME ==================

CREATE TABLE messages (
                          id BIGSERIAL PRIMARY KEY,
                          sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          receiver_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          subject VARCHAR(200),
                          content TEXT NOT NULL,
                          sent_at TIMESTAMP NOT NULL DEFAULT now(),
                          read_at TIMESTAMP
);

CREATE TABLE notifications (
                               id BIGSERIAL PRIMARY KEY,
                               user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               title VARCHAR(200) NOT NULL,
                               content TEXT,
                               is_read BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
                               id BIGSERIAL PRIMARY KEY,
                               school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
                               plan VARCHAR(50) NOT NULL,
                               start_date DATE NOT NULL,
                               end_date DATE,
                               status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED'))
);

CREATE TABLE audit_logs (
                            id BIGSERIAL PRIMARY KEY,
                            user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
                            action VARCHAR(100) NOT NULL,
                            entity VARCHAR(100) NOT NULL,
                            entity_id BIGINT,
                            created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ================== DONNÉES INITIALES ==================

INSERT INTO roles (name) VALUES
                             ('SUPER_ADMIN'),
                             ('SCHOOL_ADMIN'),
                             ('TEACHER'),
                             ('PARENT'),
                             ('STUDENT');