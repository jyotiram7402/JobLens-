-- A shared skill vocabulary, referenced by both profiles and jobs.
--
-- Until now a profile's skills lived in `user_skills` as a private value
-- collection: free text with a normalized form, owned entirely by the profile.
-- That was the right shape when nothing else had skills. It stops being the
-- right shape the moment matching has to ask "does this job's Java and this
-- user's Java refer to the same thing?", because two independent free-text
-- columns can only be compared by string equality and can drift apart forever.
--
-- So skills become their own table, and both sides point at it. The comparison
-- is then an identity check on a foreign key rather than a string match, and
-- questions like "how many openings ask for Docker" become answerable.
--
-- Deliberately NOT a skill ontology: no categories, no parent/child, no
-- synonyms table, no proficiency levels. A row is a name and its normalized
-- form. Anything more is a product decision nobody has asked for yet.

CREATE TABLE skills (
    id              UUID        PRIMARY KEY,

    -- The display form, as first written by whoever introduced the skill.
    name            VARCHAR(80) NOT NULL,

    -- The identity. Produced by TextNormalizer, the same function companies,
    -- job titles and profile preferences already use: NFKC, diacritics
    -- stripped, lowercased, punctuation to spaces, whitespace collapsed.
    -- "Spring Boot", "spring boot" and "SPRING  BOOT" therefore converge on one
    -- row, which is the entire point.
    normalized_name VARCHAR(80) NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_skills_name_not_blank CHECK (length(btrim(name)) > 0)
);

-- One row per distinct skill. This is what makes find-or-create safe under
-- concurrency: two simultaneous requests introducing "Kubernetes" cannot both
-- win, and the loser re-reads.
CREATE UNIQUE INDEX ux_skills_normalized_name ON skills (normalized_name);


CREATE TABLE job_skills (
    job_id   UUID NOT NULL REFERENCES jobs (id)   ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills (id) ON DELETE RESTRICT,

    CONSTRAINT pk_job_skills PRIMARY KEY (job_id, skill_id)
);

-- The primary key already indexes job_id as its leading column, which serves
-- "the skills for this job". This index serves the other direction -- "the jobs
-- needing this skill" -- which is how a later step will find candidates worth
-- scoring instead of scanning recent jobs.
CREATE INDEX ix_job_skills_skill_id ON job_skills (skill_id);


CREATE TABLE user_profile_skills (
    profile_id UUID NOT NULL REFERENCES user_profiles (id) ON DELETE CASCADE,
    skill_id   UUID NOT NULL REFERENCES skills (id)        ON DELETE RESTRICT,

    CONSTRAINT pk_user_profile_skills PRIMARY KEY (profile_id, skill_id)
);

CREATE INDEX ix_user_profile_skills_skill_id ON user_profile_skills (skill_id);

-- ON DELETE RESTRICT on both skill_id columns is deliberate. A skill is
-- referenced by many rows and deleting one should fail loudly rather than
-- silently emptying part of somebody's profile. There is no delete path for
-- skills anyway; unused rows are harmless.


-- ---------------------------------------------------------------------------
-- Backfill: move existing profile skills into the shared table.
--
-- gen_random_uuid() is core PostgreSQL from 13 onwards, so no extension is
-- needed. These are random (v4) UUIDs rather than the time-ordered ones the
-- application generates. That is fine for a one-off backfill of a handful of
-- rows -- the ordering property only matters for insert-heavy tables.
-- ---------------------------------------------------------------------------

INSERT INTO skills (id, name, normalized_name, created_at, updated_at, version)
SELECT gen_random_uuid(),
       -- Several profiles may have written the same skill differently.
       -- min() picks one deterministically rather than leaving it to chance.
       min(us.name),
       us.normalized_name,
       now(),
       now(),
       0
FROM user_skills us
GROUP BY us.normalized_name;

INSERT INTO user_profile_skills (profile_id, skill_id)
SELECT DISTINCT us.profile_id, s.id
FROM user_skills us
JOIN skills s ON s.normalized_name = us.normalized_name;

DROP TABLE user_skills;


COMMENT ON TABLE  skills IS 'Shared skill vocabulary referenced by both profiles and jobs.';
COMMENT ON COLUMN skills.normalized_name IS 'The identity of a skill. Produced by TextNormalizer; unique.';
COMMENT ON TABLE  job_skills IS 'Skills an opening asks for. All are treated as required in V1.';
COMMENT ON TABLE  user_profile_skills IS 'Skills a user claims.';
