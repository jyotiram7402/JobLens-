-- Development seed data. NEVER applied in production -- db/seed is only on the
-- dev profile's flyway locations. See V9001 for the full explanation.
--
-- Chosen to exercise the search rather than to look plausible: mixed casing in
-- titles, the keyword appearing in a description but not a title (and the
-- reverse), several location spellings, every work mode and employment type,
-- open-ended and fully specified experience ranges, jobs with no experience
-- stated at all, a spread of posted_at values, and one closed job.

INSERT INTO jobs (
    id, company_id, title, normalized_title, description, location,
    employment_type, work_mode, experience_min, experience_max, apply_url,
    posted_at, active, created_at, updated_at, version
) VALUES
    -- Keyword in the title.
    ('0192f4b0-0000-7000-8000-000000000001',
     '0192f4a0-0000-7000-8000-000000000001',
     'Java Backend Developer', 'java backend developer',
     'Build and maintain REST services. Experience with relational databases expected.',
     'Pune, Maharashtra, India',
     'FULL_TIME', 'HYBRID', 2, 5, 'https://example.com/apply/1',
     now() - interval '2 days', TRUE, now(), now(), 0),

    -- Keyword only in the description: proves search covers both columns.
    ('0192f4b0-0000-7000-8000-000000000002',
     '0192f4a0-0000-7000-8000-000000000001',
     'Platform Engineer', 'platform engineer',
     'Our platform runs on Java and Spring Boot. You will own deployment tooling.',
     'Hybrid - Pune',
     'FULL_TIME', 'HYBRID', 3, NULL, 'https://example.com/apply/2',
     now() - interval '5 days', TRUE, now(), now(), 0),

    -- Mixed casing, to prove the search is case-insensitive.
    ('0192f4b0-0000-7000-8000-000000000003',
     '0192f4a0-0000-7000-8000-000000000002',
     'Senior SPRING Engineer', 'senior spring engineer',
     'Lead a team building microservices.',
     'Bengaluru, India',
     'FULL_TIME', 'ONSITE', 6, 10, 'https://example.com/apply/3',
     now() - interval '1 day', TRUE, now(), now(), 0),

    -- Fully remote, and no experience stated: must still appear in an
    -- experience-filtered search rather than being dropped for lacking data.
    ('0192f4b0-0000-7000-8000-000000000004',
     '0192f4a0-0000-7000-8000-000000000002',
     'Frontend Developer', 'frontend developer',
     'React and TypeScript. Work from anywhere.',
     'Remote',
     'FULL_TIME', 'REMOTE', NULL, NULL, 'https://example.com/apply/4',
     now() - interval '10 days', TRUE, now(), now(), 0),

    -- Entry level, another spelling of the same city.
    ('0192f4b0-0000-7000-8000-000000000005',
     '0192f4a0-0000-7000-8000-000000000004',
     'Software Engineering Intern', 'software engineering intern',
     'Six-month internship working with Java services.',
     'Pune / Remote',
     'INTERNSHIP', 'REMOTE', 0, 1, 'https://example.com/apply/5',
     now() - interval '20 days', TRUE, now(), now(), 0),

    ('0192f4b0-0000-7000-8000-000000000006',
     '0192f4a0-0000-7000-8000-000000000004',
     'Contract Data Engineer', 'contract data engineer',
     'Six-month contract building data pipelines in Python.',
     'Munich, Germany',
     'CONTRACT', 'ONSITE', 4, 8, 'https://example.com/apply/6',
     now() - interval '30 days', TRUE, now(), now(), 0),

    -- Closed: must not appear in a default search.
    ('0192f4b0-0000-7000-8000-000000000007',
     '0192f4a0-0000-7000-8000-000000000001',
     'Filled Java Role', 'filled java role',
     'This position has been filled and should not appear in search results.',
     'Pune, Maharashtra, India',
     'FULL_TIME', 'ONSITE', 2, 5, NULL,
     now() - interval '45 days', FALSE, now(), now(), 0);
