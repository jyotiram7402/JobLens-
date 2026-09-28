-- Development seed data. NEVER applied in production -- db/seed is only on the
-- dev profile's flyway locations. See V9001 for the full explanation.
--
-- Gives the seeded jobs from V9002 structured skills, so the matching engine
-- has something real to score against locally. Without this every seeded job
-- would report "this job does not list the skills it needs" and skills would
-- drop out of every score -- which is correct behaviour, but useless for
-- seeing the engine work.

INSERT INTO skills (id, name, normalized_name, created_at, updated_at, version) VALUES
    ('0192f4c0-0000-7000-8000-000000000001', 'Java',        'java',        now(), now(), 0),
    ('0192f4c0-0000-7000-8000-000000000002', 'Spring Boot', 'spring boot', now(), now(), 0),
    ('0192f4c0-0000-7000-8000-000000000003', 'PostgreSQL',  'postgresql',  now(), now(), 0),
    ('0192f4c0-0000-7000-8000-000000000004', 'Docker',      'docker',      now(), now(), 0),
    ('0192f4c0-0000-7000-8000-000000000005', 'React',       'react',       now(), now(), 0),
    ('0192f4c0-0000-7000-8000-000000000006', 'TypeScript',  'typescript',  now(), now(), 0),
    ('0192f4c0-0000-7000-8000-000000000007', 'Python',      'python',      now(), now(), 0),
    ('0192f4c0-0000-7000-8000-000000000008', 'Kubernetes',  'kubernetes',  now(), now(), 0)
ON CONFLICT (normalized_name) DO NOTHING;

-- Java Backend Developer: Java, Spring Boot, PostgreSQL, Docker
INSERT INTO job_skills (job_id, skill_id) VALUES
    ('0192f4b0-0000-7000-8000-000000000001', '0192f4c0-0000-7000-8000-000000000001'),
    ('0192f4b0-0000-7000-8000-000000000001', '0192f4c0-0000-7000-8000-000000000002'),
    ('0192f4b0-0000-7000-8000-000000000001', '0192f4c0-0000-7000-8000-000000000003'),
    ('0192f4b0-0000-7000-8000-000000000001', '0192f4c0-0000-7000-8000-000000000004'),

    -- Platform Engineer: Java, Docker, Kubernetes
    ('0192f4b0-0000-7000-8000-000000000002', '0192f4c0-0000-7000-8000-000000000001'),
    ('0192f4b0-0000-7000-8000-000000000002', '0192f4c0-0000-7000-8000-000000000004'),
    ('0192f4b0-0000-7000-8000-000000000002', '0192f4c0-0000-7000-8000-000000000008'),

    -- Senior SPRING Engineer: Java, Spring Boot
    ('0192f4b0-0000-7000-8000-000000000003', '0192f4c0-0000-7000-8000-000000000001'),
    ('0192f4b0-0000-7000-8000-000000000003', '0192f4c0-0000-7000-8000-000000000002'),

    -- Frontend Developer: React, TypeScript
    ('0192f4b0-0000-7000-8000-000000000004', '0192f4c0-0000-7000-8000-000000000005'),
    ('0192f4b0-0000-7000-8000-000000000004', '0192f4c0-0000-7000-8000-000000000006'),

    -- Software Engineering Intern: Java
    ('0192f4b0-0000-7000-8000-000000000005', '0192f4c0-0000-7000-8000-000000000001'),

    -- Contract Data Engineer: Python, PostgreSQL
    ('0192f4b0-0000-7000-8000-000000000006', '0192f4c0-0000-7000-8000-000000000007'),
    ('0192f4b0-0000-7000-8000-000000000006', '0192f4c0-0000-7000-8000-000000000003')

    -- The "Filled Java Role" job (…0007) is deliberately left without skills:
    -- it is inactive, so it should never reach the matching engine at all.
ON CONFLICT DO NOTHING;
