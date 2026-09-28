-- Public job openings belonging to a company.
--
-- Follows the conventions set by V2 and V3: UUID primary keys, timestamptz
-- timestamps, declared lengths, a normalized column wherever a value will later
-- need to be matched rather than merely displayed.

CREATE TABLE jobs (
    id                UUID          PRIMARY KEY,

    -- ON DELETE RESTRICT rather than CASCADE. Companies have no delete endpoint
    -- and are hidden with their `active` flag instead, so a cascade would only
    -- ever fire by accident -- and silently destroying every opening a company
    -- ever posted is not a side effect anyone would want from an administrative
    -- action. RESTRICT makes that attempt fail loudly instead.
    company_id        UUID          NOT NULL
                                    REFERENCES companies (id) ON DELETE RESTRICT,

    title             VARCHAR(200)  NOT NULL,

    -- Derived from title by the same rules companies and skills use. Not used
    -- by search yet -- keyword search runs against the raw title and
    -- description -- but step 7 matches a profile's skills and preferred roles
    -- against jobs, and those are normalized with the same function. Storing it
    -- now means that join does not need a migration later.
    normalized_title  VARCHAR(200)  NOT NULL,

    -- Long-form text. No length cap in the database because job descriptions
    -- genuinely vary; the API caps the request at 20000 characters, which is
    -- the boundary where the limit belongs.
    description       TEXT,

    -- Free text for V1, matching companies.location. "Pune, Maharashtra,
    -- India", "Hybrid - Pune" and "Pune / Remote" are all valid, and search
    -- does a case-insensitive substring match rather than pretending this is
    -- structured. Geocoding is a later concern with its own migration.
    location          VARCHAR(200),

    employment_type   VARCHAR(20)   NOT NULL,
    work_mode         VARCHAR(20)   NOT NULL,

    -- Both nullable, because plenty of real postings do not state a range.
    -- Null means "unspecified", not "zero", and search treats it as an open
    -- bound so an unspecified job is never filtered out for lacking data the
    -- employer chose not to give.
    experience_min    SMALLINT,
    experience_max    SMALLINT,

    -- Where to actually apply. JobLens links out; it does not accept
    -- applications.
    apply_url         VARCHAR(1000),

    -- When the opening was published, which is not the same as when we recorded
    -- it. Search and the default sort use this one; created_at stays an audit
    -- field.
    posted_at         TIMESTAMPTZ   NOT NULL,

    active            BOOLEAN       NOT NULL DEFAULT TRUE,

    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_jobs_title_not_blank
        CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_jobs_normalized_title_not_blank
        CHECK (length(btrim(normalized_title)) > 0),
    CONSTRAINT ck_jobs_employment_type_known
        CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT',
                                   'INTERNSHIP', 'TEMPORARY', 'OTHER')),
    CONSTRAINT ck_jobs_work_mode_known
        CHECK (work_mode IN ('ONSITE', 'HYBRID', 'REMOTE')),
    CONSTRAINT ck_jobs_experience_non_negative
        CHECK ((experience_min IS NULL OR experience_min >= 0)
               AND (experience_max IS NULL OR experience_max >= 0)),
    CONSTRAINT ck_jobs_experience_range_ordered
        CHECK (experience_min IS NULL OR experience_max IS NULL
               OR experience_min <= experience_max),
    CONSTRAINT ck_jobs_experience_plausible
        CHECK ((experience_min IS NULL OR experience_min <= 60)
               AND (experience_max IS NULL OR experience_max <= 60))
);

-- PostgreSQL does not index a foreign key automatically. Without this, both the
-- companyId search filter and the referential integrity check on any future
-- company update would scan the whole table.
CREATE INDEX ix_jobs_company_id ON jobs (company_id);

-- Deliberately no unique constraint on (company_id, title). The same company
-- genuinely does post several openings with identical titles for different
-- teams or locations, and a constraint that rejects real data is worse than a
-- duplicate. Deduplication across aggregated sources is a V2 problem with a
-- confidence score attached.

COMMENT ON TABLE  jobs IS 'Public job openings belonging to a company.';
COMMENT ON COLUMN jobs.normalized_title IS 'Derived from title; reserved for profile-to-job matching in step 7.';
COMMENT ON COLUMN jobs.posted_at IS 'When the opening was published, not when JobLens recorded it.';
COMMENT ON COLUMN jobs.experience_min IS 'Null means unspecified, not zero.';
COMMENT ON COLUMN jobs.active IS 'Whether the opening is shown in search. Not a delete flag.';
