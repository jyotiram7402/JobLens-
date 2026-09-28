-- Indexes for the job search added in step 6.
--
-- Separate from V4 because V4 may already have been applied: an applied
-- migration is never edited, only followed by another.

-- The single most valuable index here. Almost every search is "active jobs,
-- newest first" with zero or more extra filters, so PostgreSQL can use this to
-- find the matching rows already in the right order and stop after one page --
-- no sort of the whole table, no scan of closed jobs.
--
-- Column order matters: the equality column goes first, the range/ordering
-- column second. Reversed, it would not serve the filter.
--
-- The trailing id matches the tiebreaker JobSortParser always appends, so the
-- default sort is fully covered.
CREATE INDEX ix_jobs_active_posted_at
    ON jobs (active, posted_at DESC, id);

-- Deliberately NOT indexed, with reasons:
--
--   employment_type, work_mode
--     Six values and three values respectively. An index whose every entry
--     matches a large slice of the table cannot narrow the search enough to
--     beat a scan, and PostgreSQL's planner will usually ignore it while the
--     write path still pays to maintain it. They are cheap predicates applied
--     to rows the index above already found.
--
--   title, description
--     Search is `LOWER(col) LIKE '%term%'`. A B-tree cannot serve a leading
--     wildcard, and cannot serve LOWER(col) at all without being an expression
--     index -- so a plain index on either column would be pure overhead.
--     Making that fast needs trigram indexing:
--
--       CREATE EXTENSION pg_trgm;
--       CREATE INDEX ix_jobs_title_trgm ON jobs USING gin (lower(title) gin_trgm_ops);
--
--     Not done now. It requires an extension (which a managed free-tier
--     database may or may not permit), it costs significant write time and
--     disk, and at V1 volumes the sequential scan is measured in milliseconds.
--     The moment the job table is large enough for that to stop being true,
--     this is the change to make -- and it is a migration, not a rewrite.
--
--   location
--     Same substring problem as title, same answer.
--
--   posted_at alone
--     Covered by the composite above for every query that also filters on
--     active, which is all of them by default.
--
-- ix_jobs_company_id already exists from V4.

COMMENT ON INDEX ix_jobs_active_posted_at IS
    'Serves the default search: active jobs ordered by posted_at descending.';
