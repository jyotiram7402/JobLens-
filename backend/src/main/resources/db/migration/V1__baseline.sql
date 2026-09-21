-- V1 baseline.
--
-- Business tables (companies, jobs, users) arrive in later steps, each in its
-- own versioned migration. This baseline only records that Flyway owns the
-- schema from here on, so the very first deployment has a migration to apply
-- and the history table is created.

CREATE TABLE IF NOT EXISTS schema_metadata (
    key         TEXT PRIMARY KEY,
    value       TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO schema_metadata (key, value)
VALUES ('baseline', 'v1')
ON CONFLICT (key) DO NOTHING;
