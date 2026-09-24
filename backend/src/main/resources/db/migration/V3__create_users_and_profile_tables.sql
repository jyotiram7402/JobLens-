-- Users, their career profile, and the three preference collections.
--
-- Shape follows the conventions set by V2: UUID primary keys, timestamptz
-- timestamps, declared lengths, and a normalized column wherever a value will
-- later need to be matched rather than merely displayed.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,

    -- 254 is the practical maximum length of an email address (RFC 5321).
    -- Stored already lowercased and trimmed by the application, so the unique
    -- index below is genuinely case-insensitive without needing citext or a
    -- functional index.
    email         VARCHAR(254) NOT NULL,

    -- A bcrypt hash is 60 characters; the column is wider because the
    -- DelegatingPasswordEncoder prefixes the algorithm, as in "{bcrypt}$2a$10$...".
    -- That prefix is what lets us move to a stronger encoder later without
    -- invalidating existing passwords.
    password_hash VARCHAR(100) NOT NULL,

    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,

    -- One role is enough for V1. Kept as a column rather than a join table so
    -- that adding ADMIN later is a value change, not a schema change; a
    -- many-roles-per-user model can arrive if it is ever actually needed.
    role          VARCHAR(32)  NOT NULL DEFAULT 'USER',

    active        BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_users_email_not_blank   CHECK (length(btrim(email)) > 0),
    CONSTRAINT ck_users_email_lowercase   CHECK (email = lower(email)),
    CONSTRAINT ck_users_role_known        CHECK (role IN ('USER', 'ADMIN'))
);

-- Login looks a user up by email on every attempt, and registration checks it
-- for duplicates. Unique both to enforce one account per address and to serve
-- those lookups.
CREATE UNIQUE INDEX ux_users_email ON users (email);


CREATE TABLE user_profiles (
    id                  UUID         PRIMARY KEY,

    -- UNIQUE enforces one profile per user; ON DELETE CASCADE means a profile
    -- cannot outlive its user. A profile is created together with the account,
    -- so this column is never null and there is no orphan to clean up.
    user_id             UUID         NOT NULL UNIQUE
                                     REFERENCES users (id) ON DELETE CASCADE,

    headline            VARCHAR(200),
    summary             VARCHAR(2000),

    -- SMALLINT is ample and the CHECK keeps the value meaningful: negative
    -- experience is nonsense, and 60 years is beyond any real career.
    years_of_experience SMALLINT,

    -- Deliberately not called "current_role": CURRENT_ROLE is a reserved word
    -- in SQL, and a column that needs quoting everywhere is a permanent tax.
    current_job_title   VARCHAR(150),

    remote_preference   VARCHAR(20)  NOT NULL DEFAULT 'ANY',

    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_user_profiles_experience_range
        CHECK (years_of_experience IS NULL
               OR (years_of_experience >= 0 AND years_of_experience <= 60)),
    CONSTRAINT ck_user_profiles_remote_preference_known
        CHECK (remote_preference IN ('REMOTE', 'HYBRID', 'ONSITE', 'ANY'))
);

-- No separate index on user_id: the UNIQUE constraint above already creates
-- one, and it is the only way this table is queried.


-- The three preference collections below share a shape on purpose. Each stores
-- the text as the user typed it plus a normalized form, produced by the same
-- rules the company domain uses. The normalized column is what step 7 will join
-- on when matching a profile against a job, and it is what makes
-- (profile_id, normalized_*) a meaningful uniqueness rule: adding "java" after
-- "Java" is not a second skill.
--
-- They are value collections, not entities: the composite primary key is the
-- natural one, and a row has no identity or lifecycle apart from its profile.

CREATE TABLE user_skills (
    profile_id      UUID        NOT NULL
                                REFERENCES user_profiles (id) ON DELETE CASCADE,
    name            VARCHAR(80) NOT NULL,
    normalized_name VARCHAR(80) NOT NULL,

    CONSTRAINT pk_user_skills PRIMARY KEY (profile_id, normalized_name),
    CONSTRAINT ck_user_skills_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE TABLE user_preferred_roles (
    profile_id       UUID         NOT NULL
                                  REFERENCES user_profiles (id) ON DELETE CASCADE,
    title            VARCHAR(150) NOT NULL,
    normalized_title VARCHAR(150) NOT NULL,

    CONSTRAINT pk_user_preferred_roles PRIMARY KEY (profile_id, normalized_title),
    CONSTRAINT ck_user_preferred_roles_title_not_blank CHECK (length(btrim(title)) > 0)
);

CREATE TABLE user_preferred_locations (
    profile_id      UUID         NOT NULL
                                 REFERENCES user_profiles (id) ON DELETE CASCADE,
    name            VARCHAR(150) NOT NULL,
    normalized_name VARCHAR(150) NOT NULL,

    CONSTRAINT pk_user_preferred_locations PRIMARY KEY (profile_id, normalized_name),
    CONSTRAINT ck_user_preferred_locations_name_not_blank CHECK (length(btrim(name)) > 0)
);

-- The composite primary keys already index profile_id as their leading column,
-- which is how every one of these tables is read (always "all rows for this
-- profile"). No further indexes are needed.

COMMENT ON TABLE  users IS 'Accounts. Contains a password hash, never a password.';
COMMENT ON COLUMN users.email IS 'Lowercased and trimmed by the application before storage.';
COMMENT ON COLUMN users.password_hash IS 'Spring Security DelegatingPasswordEncoder output, including the {algorithm} prefix.';
COMMENT ON TABLE  user_profiles IS 'Career profile, one per user, created with the account.';
COMMENT ON COLUMN user_profiles.current_job_title IS 'Named to avoid the SQL reserved word CURRENT_ROLE.';
