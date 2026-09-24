-- Development seed data. NEVER applied in production.
--
-- This file lives in classpath:db/seed, which only the dev profile adds to
-- spring.flyway.locations (see application-dev.yml). The prod profile never
-- sees it, so these rows cannot reach a real database.
--
-- The version number is deliberately far above the real migrations so seed data
-- always runs last and never sits between two schema changes.
--
-- Note for anyone pointing a prod-profile application at a database that was
-- previously used for development: Flyway will complain about an applied
-- migration it cannot resolve. Use a separate database, which is what the
-- compose file and the deployment guide already assume.
--
-- Values are chosen to exercise the real behaviour: mixed case, extra spacing,
-- an accent, and punctuation, all of which must normalize predictably.

INSERT INTO companies (
    id, name, normalized_name, slug, description, website_url, careers_url,
    logo_url, industry, location, active, created_at, updated_at, version
) VALUES
    ('0192f4a0-0000-7000-8000-000000000001',
     'Tata Consultancy Services',
     'tata consultancy services',
     'tata-consultancy-services',
     'Information technology services and consulting company.',
     'https://www.tcs.com', 'https://www.tcs.com/careers', NULL,
     'Information Technology', 'Mumbai, India',
     TRUE, now(), now(), 0),

    ('0192f4a0-0000-7000-8000-000000000002',
     'Infosys Limited',
     'infosys limited',
     'infosys-limited',
     'Technology services and consulting company.',
     'https://www.infosys.com', 'https://www.infosys.com/careers', NULL,
     'Information Technology', 'Bengaluru, India',
     TRUE, now(), now(), 0),

    -- Punctuation: "Nestlé S.A." normalizes to "nestle s a".
    ('0192f4a0-0000-7000-8000-000000000003',
     'Nestlé S.A.',
     'nestle s a',
     'nestle-s-a',
     'Food and beverage company.',
     'https://www.nestle.com', NULL, NULL,
     'Food and Beverage', 'Vevey, Switzerland',
     TRUE, now(), now(), 0),

    ('0192f4a0-0000-7000-8000-000000000004',
     'Siemens AG',
     'siemens ag',
     'siemens-ag',
     'Industrial manufacturing and automation company.',
     'https://www.siemens.com', 'https://jobs.siemens.com', NULL,
     'Industrial Manufacturing', 'Munich, Germany',
     TRUE, now(), now(), 0),

    -- Inactive, so search results can be checked for excluding it.
    ('0192f4a0-0000-7000-8000-000000000005',
     'Dormant Test Company',
     'dormant test company',
     'dormant-test-company',
     'Inactive on purpose: verifies that search filters on active.',
     NULL, NULL, NULL,
     'Testing', 'Nowhere',
     FALSE, now(), now(), 0);
