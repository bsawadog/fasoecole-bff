-- =========================================================
-- V2__seed_test_data.sql
-- Données de test (environnement local / dev uniquement)
-- Mot de passe en clair pour tous les utilisateurs : password123
-- =========================================================

-- ================== UTILISATEURS ==================
-- id 1: admin école, 2: enseignant Jean, 3: enseignant Awa,
-- id 4: parent Moussa, id 5: parent Fatou,
-- id 6-9: élèves (Ali, Aicha, Ibrahim, Salimata)

INSERT INTO users (id, first_name, last_name, email, password_hash, phone, active) VALUES
    (2, 'Jean', 'Ouedraogo', 'jean.ouedraogo@fasoecole.com', '$2b$10$M74jBHz0yEWggfM/fqR.qelz9xyKbRahCftIGYK0CgLUwjbJqEbJq', '+22670000002', TRUE),
    (3, 'Awa', 'Traore', 'awa.traore@fasoecole.com', '$2b$10$M74jBHz0yEWggfM/fqR.qelz9xyKbRahCftIGYK0CgLUwjbJqEbJq', '+22670000003', TRUE),
    (4, 'Moussa', 'Kabore', 'moussa.kabore@fasoecole.com', '$2b$10$M74jBHz0yEWggfM/fqR.qelz9xyKbRahCftIGYK0CgLUwjbJqEbJq', '+22670000004', TRUE),
    (5, 'Fatou', 'Sawadogo', 'fatou.sawadogo@fasoecole.com', '$2b$10$M74jBHz0yEWggfM/fqR.qelz9xyKbRahCftIGYK0CgLUwjbJqEbJq', '+22670000005', TRUE),
    (6, 'Ali', 'Kabore', 'ali.kabore@fasoecole.com', '$2b$10$M74jBHz0yEWggfM/fqR.qelz9xyKbRahCftIGYK0CgLUwjbJqEbJq', '+22670000006', TRUE),
    (7, 'Aicha', 'Kabore', 'aicha.kabore@fasoecole.com', '$2b$10$M74jBHz0yEWggfM/fqR.qelz9xyKbRahCftIGYK0CgLUwjbJqEbJq', '+22670000007', TRUE),
    (8, 'Ibrahim', 'Sawadogo', 'ibrahim.sawadogo@fasoecole.com', '$2b$10$M74jBHz0yEWggfM/fqR.qelz9xyKbRahCftIGYK0CgLUwjbJqEbJq', '+22670000008', TRUE),
    (9, 'Salimata', 'Sawadogo', 'salimata.sawadogo@fasoecole.com', '$2b$10$M74jBHz0yEWggfM/fqR.qelz9xyKbRahCftIGYK0CgLUwjbJqEbJq', '+22670000009', TRUE);

SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));

-- ================== ÉCOLE ==================

INSERT INTO schools (id, name, type, address, phone, email, owner_id, status) VALUES
    (1, 'Ecole Primaire La Reussite', 'PRIMAIRE', 'Ouagadougou, Secteur 15', '+22625000000', 'contact@lareussite.bf', 1, 'ACTIVE');

SELECT setval('schools_id_seq', (SELECT MAX(id) FROM schools));

-- ================== RÔLES UTILISATEUR PAR ÉCOLE ==================
-- roles: 1=SUPER_ADMIN, 2=SCHOOL_ADMIN, 3=TEACHER, 4=PARENT, 5=STUDENT

INSERT INTO school_users (user_id, school_id, role_id) VALUES
    (1, 1, 2), -- Admin -> SCHOOL_ADMIN
    (2, 1, 3), -- Jean -> TEACHER
    (3, 1, 3), -- Awa -> TEACHER
    (4, 1, 4), -- Moussa -> PARENT
    (5, 1, 4), -- Fatou -> PARENT
    (6, 1, 5), -- Ali -> STUDENT
    (7, 1, 5), -- Aicha -> STUDENT
    (8, 1, 5), -- Ibrahim -> STUDENT
    (9, 1, 5); -- Salimata -> STUDENT

-- ================== STRUCTURE ACADÉMIQUE ==================

INSERT INTO academic_years (id, school_id, label, start_date, end_date, is_current) VALUES
    (1, 1, '2025-2026', '2025-10-01', '2026-06-30', TRUE);

SELECT setval('academic_years_id_seq', (SELECT MAX(id) FROM academic_years));

INSERT INTO levels (id, school_id, name, cycle, order_index) VALUES
    (1, 1, 'CP1', 'PRIMAIRE', 1),
    (2, 1, 'CE1', 'PRIMAIRE', 2);

SELECT setval('levels_id_seq', (SELECT MAX(id) FROM levels));

INSERT INTO classes (id, school_id, academic_year_id, level_id, name, capacity) VALUES
    (1, 1, 1, 1, 'CP1-A', 40),
    (2, 1, 1, 2, 'CE1-A', 40);

SELECT setval('classes_id_seq', (SELECT MAX(id) FROM classes));

INSERT INTO subjects (id, school_id, name, code) VALUES
    (1, 1, 'Mathematiques', 'MATH'),
    (2, 1, 'Francais', 'FR');

SELECT setval('subjects_id_seq', (SELECT MAX(id) FROM subjects));

-- ================== ENSEIGNANTS ==================

INSERT INTO teachers (id, user_id, school_id, specialty, hire_date) VALUES
    (1, 2, 1, 'Mathematiques', '2020-09-01'),
    (2, 3, 1, 'Francais', '2021-09-01');

SELECT setval('teachers_id_seq', (SELECT MAX(id) FROM teachers));

INSERT INTO class_subject_teacher (id, class_id, subject_id, teacher_id, coefficient) VALUES
    (1, 1, 1, 1, 2.0),
    (2, 1, 2, 2, 2.0),
    (3, 2, 1, 1, 2.0),
    (4, 2, 2, 2, 2.0);

SELECT setval('class_subject_teacher_id_seq', (SELECT MAX(id) FROM class_subject_teacher));

-- ================== ÉLÈVES ==================

INSERT INTO students (id, user_id, school_id, registration_number, birth_date, gender) VALUES
    (1, 6, 1, 'MAT-2025-001', '2018-03-12', 'M'),
    (2, 7, 1, 'MAT-2025-002', '2018-07-25', 'F'),
    (3, 8, 1, 'MAT-2025-003', '2017-01-05', 'M'),
    (4, 9, 1, 'MAT-2025-004', '2017-11-20', 'F');

SELECT setval('students_id_seq', (SELECT MAX(id) FROM students));

INSERT INTO student_enrollments (id, student_id, class_id, academic_year_id, status, enrollment_date) VALUES
    (1, 1, 1, 1, 'ACTIVE', '2025-10-01'),
    (2, 2, 1, 1, 'ACTIVE', '2025-10-01'),
    (3, 3, 2, 1, 'ACTIVE', '2025-10-01'),
    (4, 4, 2, 1, 'ACTIVE', '2025-10-01');

SELECT setval('student_enrollments_id_seq', (SELECT MAX(id) FROM student_enrollments));

-- ================== PARENTS ==================

INSERT INTO parents (id, user_id) VALUES
    (1, 4), -- Moussa Kabore
    (2, 5); -- Fatou Sawadogo

SELECT setval('parents_id_seq', (SELECT MAX(id) FROM parents));

INSERT INTO parent_student (id, parent_id, student_id, relationship) VALUES
    (1, 1, 1, 'PERE'),
    (2, 1, 2, 'PERE'),
    (3, 2, 3, 'MERE'),
    (4, 2, 4, 'MERE');

SELECT setval('parent_student_id_seq', (SELECT MAX(id) FROM parent_student));

-- ================== PÉDAGOGIE ==================

INSERT INTO grades (id, student_id, class_subject_teacher_id, term, type, value, max_value, grade_date) VALUES
    (1, 1, 1, 'TERM1', 'DEVOIR', 15.5, 20, '2025-11-10'),
    (2, 1, 2, 'TERM1', 'DEVOIR', 12.0, 20, '2025-11-11'),
    (3, 2, 1, 'TERM1', 'DEVOIR', 17.0, 20, '2025-11-10'),
    (4, 3, 3, 'TERM1', 'DEVOIR', 14.0, 20, '2025-11-12'),
    (5, 4, 4, 'TERM1', 'DEVOIR', 16.5, 20, '2025-11-12');

SELECT setval('grades_id_seq', (SELECT MAX(id) FROM grades));

INSERT INTO attendances (id, student_id, class_id, attendance_date, status, justification) VALUES
    (1, 1, 1, '2025-11-03', 'PRESENT', NULL),
    (2, 2, 1, '2025-11-03', 'ABSENT', NULL),
    (3, 3, 2, '2025-11-03', 'LATE', 'Bus en retard'),
    (4, 4, 2, '2025-11-03', 'EXCUSED', 'Rendez-vous medical');

SELECT setval('attendances_id_seq', (SELECT MAX(id) FROM attendances));

INSERT INTO report_cards (id, student_id, academic_year_id, term, average, rank, comment, validated) VALUES
    (1, 1, 1, 'TERM1', 13.75, 2, 'Bon trimestre', TRUE),
    (2, 2, 1, 'TERM1', 17.0, 1, 'Excellent travail', TRUE),
    (3, 3, 1, 'TERM1', 14.0, 1, 'Peut mieux faire', FALSE),
    (4, 4, 1, 'TERM1', 16.5, 2, 'Tres bon niveau', FALSE);

SELECT setval('report_cards_id_seq', (SELECT MAX(id) FROM report_cards));

INSERT INTO documents (id, school_id, class_id, uploaded_by, title, file_url, type) VALUES
    (1, 1, 1, 1, 'Reglement interieur', 'https://example.com/documents/reglement.pdf', 'ADMINISTRATIF'),
    (2, 1, 2, 2, 'Support cours Mathematiques CE1', 'https://example.com/documents/math-ce1.pdf', 'PEDAGOGIQUE');

SELECT setval('documents_id_seq', (SELECT MAX(id) FROM documents));

-- ================== FINANCES ==================

INSERT INTO fee_types (id, school_id, name, amount, frequency) VALUES
    (1, 1, 'Frais de scolarite', 50000, 'YEARLY'),
    (2, 1, 'Frais de cantine', 5000, 'MONTHLY');

SELECT setval('fee_types_id_seq', (SELECT MAX(id) FROM fee_types));

INSERT INTO invoices (id, student_id, fee_type_id, academic_year_id, amount_due, due_date, status) VALUES
    (1, 1, 1, 1, 50000, '2025-11-30', 'PAID'),
    (2, 2, 1, 1, 50000, '2025-11-30', 'PENDING'),
    (3, 3, 2, 1, 5000, '2025-12-05', 'PENDING'),
    (4, 4, 2, 1, 5000, '2025-12-05', 'OVERDUE');

SELECT setval('invoices_id_seq', (SELECT MAX(id) FROM invoices));

INSERT INTO payments (id, invoice_id, amount, payment_date, method, reference) VALUES
    (1, 1, 50000, '2025-11-15', 'MOBILE_MONEY', 'PMT-0001');

SELECT setval('payments_id_seq', (SELECT MAX(id) FROM payments));

-- ================== COMMUNICATION & PLATEFORME ==================

INSERT INTO messages (id, sender_id, receiver_id, subject, content, sent_at, read_at) VALUES
    (1, 1, 4, 'Reunion parents-professeurs', 'Une reunion aura lieu le 15 decembre a 15h.', '2025-11-20 09:00:00', NULL),
    (2, 4, 1, 'Question sur les frais', 'Bonjour, puis-je payer les frais de cantine en 2 fois ?', '2025-11-20 10:30:00', '2025-11-20 11:00:00');

SELECT setval('messages_id_seq', (SELECT MAX(id) FROM messages));

INSERT INTO notifications (id, user_id, title, content, is_read) VALUES
    (1, 4, 'Facture emise', 'Une nouvelle facture de frais de cantine a ete emise.', FALSE),
    (2, 1, 'Nouvelle inscription', 'Un nouvel eleve a ete inscrit en CE1-A.', TRUE);

SELECT setval('notifications_id_seq', (SELECT MAX(id) FROM notifications));

INSERT INTO subscriptions (id, school_id, plan, start_date, end_date, status) VALUES
    (1, 1, 'STANDARD', '2025-09-01', '2026-08-31', 'ACTIVE');

SELECT setval('subscriptions_id_seq', (SELECT MAX(id) FROM subscriptions));

INSERT INTO audit_logs (id, user_id, action, entity, entity_id) VALUES
    (1, 1, 'CREATE', 'STUDENT', 1),
    (2, 1, 'CREATE', 'INVOICE', 1),
    (3, 4, 'UPDATE', 'PAYMENT', 1);

SELECT setval('audit_logs_id_seq', (SELECT MAX(id) FROM audit_logs));
