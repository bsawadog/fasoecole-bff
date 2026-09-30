INSERT INTO schools (name, type, address, phone, email, owner_id, status)
SELECT demo.name, demo.type, demo.address, demo.phone, demo.email, 1, 'ACTIVE'
FROM (VALUES
    ('Lycée Wendpanga', 'SECONDAIRE', 'Ouagadougou, Zone du Bois', '+22625000002', 'contact@wendpanga.bf'),
    ('Université du Faso', 'UNIVERSITE', 'Ouagadougou, Kossodo', '+22625000003', 'contact@unifaso.bf'),
    ('Centre de Formation professionnelle', 'FORMATION', 'Bobo-Dioulasso, Centre-ville', '+22625000004', 'contact@cfp.bf')
) AS demo(name, type, address, phone, email)
WHERE NOT EXISTS (SELECT 1 FROM schools school WHERE school.name = demo.name);

INSERT INTO users (
    first_name, last_name, email, password_hash, phone, active, approved,
    requested_school_id, requested_role
)
SELECT
    demo.first_name,
    demo.last_name,
    demo.email,
    '$2b$10$M74jBHz0yEWggfM/fqR.qelz9xyKbRahCftIGYK0CgLUwjbJqEbJq',
    demo.phone,
    TRUE,
    demo.approved,
    school.id,
    demo.requested_role
FROM (VALUES
    ('Idrissa', 'Sawadogo', 'demo.enseignant@fasoecole.com', '+22670000010', TRUE, 'Lycée Wendpanga', 'TEACHER'),
    ('Aminata', 'Kaboré', 'demo.parent@fasoecole.com', '+22670000011', TRUE, 'Ecole Primaire La Reussite', 'PARENT'),
    ('Mariam', 'Traoré', 'demo.etudiant@fasoecole.com', '+22670000012', TRUE, 'Université du Faso', 'STUDENT'),
    ('Salif', 'Compaoré', 'demo.formation@fasoecole.com', '+22670000013', TRUE, 'Centre de Formation professionnelle', 'TEACHER'),
    ('Issa', 'Zongo', 'attente.enseignant@fasoecole.com', '+22670000014', FALSE, 'Ecole Primaire La Reussite', 'TEACHER'),
    ('Awa', 'Ouédraogo', 'attente.parent@fasoecole.com', '+22670000015', FALSE, 'Lycée Wendpanga', 'PARENT'),
    ('Fatimata', 'Ki', 'attente.etudiant@fasoecole.com', '+22670000016', FALSE, 'Université du Faso', 'STUDENT')
) AS demo(first_name, last_name, email, phone, approved, school_name, requested_role)
JOIN schools school ON school.name = demo.school_name
ON CONFLICT (email) DO NOTHING;

INSERT INTO school_users (user_id, school_id, role_id)
SELECT users.id, school.id, roles.id
FROM (VALUES
    ('demo.enseignant@fasoecole.com', 'Lycée Wendpanga', 'TEACHER'),
    ('demo.parent@fasoecole.com', 'Ecole Primaire La Reussite', 'PARENT'),
    ('demo.etudiant@fasoecole.com', 'Université du Faso', 'STUDENT'),
    ('demo.formation@fasoecole.com', 'Centre de Formation professionnelle', 'TEACHER')
) AS demo(email, school_name, role_name)
JOIN users ON users.email = demo.email
JOIN schools school ON school.name = demo.school_name
JOIN roles ON roles.name = demo.role_name
ON CONFLICT (user_id, school_id, role_id) DO NOTHING;
