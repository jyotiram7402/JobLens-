# Architecture

## Current architecture

```
+-------------------------+
| React + TypeScript      |   Vite dev server / static build on a CDN
| (browser)               |
+-----------+-------------+
            | HTTPS, JSON, /api/v1/**
+-----------v-------------+
| Spring Boot REST API    |   Java 21, modular monolith
|  - config/              |
|  - common/              |   cross-cutting: error shape, web plumbing
|  - <domain modules>     |   company, job, user, matching (later steps)
+-----------+-------------+
            | JDBC
+-----------v-------------+
| PostgreSQL 16           |   schema owned by Flyway
+-------------------------+
```

Everything currently in the repository is foundation: application bootstrap,
configuration, CORS, a uniform error response, health endpoints, the Flyway
baseline, and a frontend shell that proves it can reach the API. There is no
business logic yet, by design.

## Why a modular monolith

JobLens is one product with one team and one database. Splitting it into
services now would buy nothing and cost a lot:

- **Transactions stay simple.** A scan touches companies, jobs and user
  tracking. In one process that is one database transaction; across services it
  is distributed state to reconcile.
- **One deployment.** On free-tier hosting, every extra service is another cold
  start, another environment to configure, another thing to keep awake.
- **Cheaper to change.** Domain boundaries are still being discovered. Moving a
  boundary inside a codebase is a refactor; moving it between services is a
  migration.

The discipline is in the package structure, not the process count. Each domain
gets its own package (`company`, `job`, `user`, `matching`) that owns its
entities, repository and service, and exposes behaviour to other domains only
through its service interface, never by reaching into another domain's
repository. If a module ever genuinely needs to be extracted, that boundary is
already where it would be cut.

Kafka, Redis and any split into services are postponed deliberately, not
overlooked — see [ADR 0001](docs/decisions/0001-modular-monolith.md) and
"Future evolution path" below. The concrete rules are under
"Backend architecture → Module rules".

## Backend architecture

### Package structure

The base package is `com.joblens.api`. Everything below it is either the shared
kernel, application-wide configuration, or one domain module.

```
com.joblens.api
├── JobLensApplication.java      entry point; scanning starts here
│
├── config/                      application-wide wiring only
│   ├── CorsConfig.java          allowlisted origins for /api/v1/**
│   ├── CorsProperties.java      typed, validated joblens.cors.* binding
│   └── JpaConfig.java           auditing, with UTC timestamps
│
├── common/                      shared kernel -- depends on nothing
│   ├── domain/BaseEntity.java   id, audit timestamps, optimistic lock version
│   ├── exception/               ErrorCode, ApplicationException hierarchy,
│   │                            GlobalExceptionHandler
│   ├── response/                ApiError, PageResponse
│   └── web/                     ApiRoutes, CorrelationIdFilter, MetaController
│
├── user/          step 4        accounts, credentials, profile
├── company/       step 3        the canonical company record and its search
├── job/           step 5        public openings belonging to a company
├── matching/      step 7        scoring a job against a profile
├── tracking/      step 10       followed companies and application status
├── scan/          step 11       image capture and resolution to a company
└── notification/  V2            telling a user about something
```

The domain packages exist as boundaries and contain a `package-info.java` and
nothing else. They are not populated ahead of their roadmap step, and no
placeholder CRUD was written to make them look busy.

### Module rules

Two rules keep the monolith modular rather than merely single-process:

1. **`common` depends on nothing.** It must never import from a domain module.
   A dependency in that direction turns the shared kernel into a cycle.
2. **Modules talk through services, never repositories.** `job` may call
   `CompanyService`; it may not touch `CompanyRepository` or a company entity's
   table. This is the boundary that would become a network call if a module
   were ever extracted.

Enforcement is currently review discipline. An ArchUnit test could make it
mechanical once there are enough modules for that to be worth the dependency.

### Layering inside a module

```
Controller   HTTP only: routing, request validation, DTO in and out.
             No business rules. Never returns an entity.
    │
Service      Business rules and the transaction boundary (@Transactional).
             Knows nothing about HTTP -- no ResponseEntity, no status codes.
    │
Repository   Spring Data JPA. Persistence only.
    │
Entity       Owned by its module, never serialised to a client.
```

DTOs are Java records: immutable, with validation annotations on the request
types. Entities never leave the service layer, because exposing one couples the
public API to the schema and makes every column rename a breaking change.

Dependencies are injected through constructors. No field injection, so a class
cannot be constructed in an invalid state and tests need no reflection.

### Error handling

Every failure leaves through `GlobalExceptionHandler` and arrives as one shape:

```json
{
  "timestamp": "2026-09-22T10:15:30Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/companies",
  "traceId": "6f1c2b9e4a7d4c31",
  "details": { "name": "must not be blank" }
}
```

`error` is an `ErrorCode` constant and part of the public contract -- clients
branch on it, so it may not be renamed casually. `message` is human-readable and
free to change. `details` is omitted when empty.

The handler extends `ResponseEntityExceptionHandler`, so the responses Spring
MVC generates itself -- unreadable JSON, wrong method, unsupported media type --
come back in the same shape instead of Spring's default body. A client therefore
only ever parses one error format.

The important distinction is expected versus unexpected:

- An `ApplicationException` (`ResourceNotFoundException`,
  `DuplicateResourceException`, …) is expected. Its message was written for the
  caller and is returned verbatim, logged at WARN.
- Anything else is a bug. It is logged at ERROR with the stack trace, and the
  client is told only that something went wrong. Exception messages routinely
  contain table names, SQL fragments and file paths; none of that belongs in a
  response.

### Request correlation

`CorrelationIdFilter` runs first in the chain and gives every request an id,
published three ways: into the logging MDC (the log pattern prints it on every
line), onto the `X-Correlation-Id` response header, and into the error body as
`traceId`. An inbound header is honoured, so one trace can later span the
frontend, the backend and the AI service without adding a tracing platform.

A user can quote the id from an error and we can find the exact request.

### Logging

Standard SLF4J over Logback, configured in `application.yml` -- no logging
framework of our own, and no observability platform yet. The console pattern
includes `%X{traceId}`. Development logs at DEBUG with SQL and bind parameters;
production logs at INFO with neither.

Credentials, tokens, API keys and password fields are never logged, at any
level. That is why `show-sql` is off in production: query text can contain user
data.

### Configuration and profiles

```
application.yml        shared, environment-neutral. No credentials, no hosts.
application-dev.yml    local defaults that work against docker-compose.
application-prod.yml   no defaults at all -- every value from the environment.
application-test.yml   (test resources) points at a throwaway database.
```

The active profile defaults to `dev`, so a developer who sets nothing gets the
safe local setup. Production sets `SPRING_PROFILES_ACTIVE=prod`, where a missing
variable fails startup with a named placeholder rather than silently falling
back to something local. A deployment that cannot reach its database should not
come up looking healthy.

CORS is bound to a validated `CorsProperties` record rather than scattered
`@Value` lookups, so a typo in a property name fails at startup and `@NotEmpty`
makes an unconfigured allowlist impossible. There is no wildcard origin and no
`allowCredentials` -- authentication will use a bearer token, not a cookie.
Only `/api/v1/**` is exposed; Actuator deliberately is not.

### Database strategy

Spring Data JPA over PostgreSQL, with Flyway owning the schema exclusively.

- `ddl-auto: validate`. Hibernate checks its mapping against the migrated
  schema and refuses to start on a mismatch. It never alters a table.
- `open-in-view: false`. Keeping a session open across the view layer hides
  lazy loading and turns one query into hundreds.
- `BaseEntity` gives every entity a database-generated id, UTC audit
  timestamps, and an optimistic-locking `version` so two concurrent updates
  fail loudly instead of silently overwriting each other.
- Timestamps are `Instant`, stored and compared as UTC.

Migrations so far:

| Version | Purpose |
| ------- | ------- |
| `V1__baseline.sql` | Establishes Flyway ownership. No business tables. |
| `V2__create_companies_table.sql` | The `companies` table, its constraints and indexes. |
| `V3__create_users_and_profile_tables.sql` | `users`, `user_profiles`, and the three preference collections. |
| `V4__create_jobs_table.sql` | The `jobs` table, its constraints and its foreign key index. |
| `V5__add_job_search_indexes.sql` | The composite index serving the default job search. |
| `V6__create_skills_tables.sql` | The shared `skills` vocabulary plus `job_skills` and `user_profile_skills`, backfilled from the old `user_skills` table, which it drops. |

Each domain gets its own numbered migration in its own step. Applied migrations
are never edited or renamed — a change means a new version.

Development seed data lives in `db/seed` and is added to `spring.flyway.locations`
**only by the dev profile**, so seeded rows cannot reach production.

### The Company domain

Companies are the spine of JobLens. A scan resolves to one, jobs belong to one,
tracking follows one — so almost everything later points here, and the module
owns three things nothing else is allowed to reimplement: what a company *is*,
when two records are the *same* company, and what a company is *called* in a URL.

Other modules will use `CompanyService`. Nothing outside the package touches
`CompanyRepository` or the `Company` entity.

#### Identifiers: UUID and slug

Primary keys are **time-ordered UUIDs** (`UuidGenerator.Style.TIME`), stored in
PostgreSQL's native 16-byte `uuid` type.

The reason is exposure, not fashion. Ids appear in URLs, so a sequential
`bigint` would publish how many companies exist and let anyone walk the entire
table by counting upwards. That is a real problem for a product whose value is
its dataset. A UUID also exists before the row does, which the scan flow will
need to reference a company it is still resolving.

The cost is honest: 16 bytes instead of 8, in this key and in every future
foreign key. What makes it affordable is the *time-ordered* part. Random
(version 4) UUIDs scatter inserts across the whole B-tree, so every insert
dirties a different page and the index fragments badly; time-ordered values
append to the right-hand edge like a sequence, keeping inserts and index size
close to a `bigint`. Choosing UUID v4 here would have been the version that
deserves the "sounds advanced, costs real performance" criticism.

A UUID is not a readable URL, so every company also has a **slug**
(`tata-consultancy-services`). The two identifiers have different jobs: the UUID
is the stable key other resources reference; the slug is what a human sees.
Names are not unique, so collisions get a numeric suffix (`acme`, `acme-2`), and
a unique index — not the application check — is what guarantees it.

**A slug is assigned once and never regenerated**, including when the company is
renamed. It is a public identifier already present in links and caches;
recomputing it on rename would silently break all of them.

#### Timestamps

`Instant`, stored as `timestamptz`, always UTC. An `Instant` is a moment on the
timeline with no zone of its own, so it cannot be misread as local time. The
database server, the container and a developer's laptop all disagree about
"local", and none of them is allowed to influence a stored value; formatting for
a user's zone is the frontend's job.

They are set by Spring Data auditing rather than by hand or by trigger, so a
service cannot forget one and cannot fake one.

#### Normalized names

Every company stores a `normalized_name` derived deterministically from `name`:
NFKC, diacritics stripped, lowercased with `Locale.ROOT`, non-alphanumerics
replaced with spaces, whitespace collapsed.

```
"Tata Consultancy Services"    ┐
"TATA CONSULTANCY SERVICES"    ├─→ "tata consultancy services"
"Tata  Consultancy  Services"  ┘
"Nestlé S.A."                  ──→ "nestle s a"
```

Rule-based and not AI, because the result is stored in a unique index: it has to
be reproducible on every machine, forever, with no model version or network call
involved. It is what duplicate detection compares and what search matches
against, which is why searching is insensitive to case, spacing, accents and
punctuation without a single `LOWER()` in a query.

A useful side effect: a normalized search term cannot contain `LIKE` wildcards,
because `%` and `_` are removed before the value reaches a query.

**It deliberately does not strip legal suffixes.** `Acme Ltd` and `Acme` stay
two records. Deciding that `Ltd`, `Inc` and `GmbH` are noise is entity
resolution, not normalization: it requires judgement per jurisdiction, it merges
companies that genuinely are distinct legal entities, and a unique index makes a
wrong merge permanent. That belongs to the AI company-resolution step, where a
confidence score and human review exist. Same reasoning for transliteration and
abbreviation expansion.

#### Duplicate handling

Two layers, and the order matters:

1. The service looks up the normalized name first, purely to produce a good
   error — one that names the existing company's slug so the caller can link to
   it.
2. The **unique index on `normalized_name`** is what actually prevents the
   duplicate. The check and the insert are not atomic, so two simultaneous
   requests can both pass step 1; only the database can settle that. The service
   catches the violation and turns it into the same `409`.

Writing only the application check would look correct and fail under
concurrency; writing only the constraint would produce a 500 and a stack trace.

#### Why entities are not exposed

Controllers return `CompanyResponse` and `CompanySummary`, never `Company`.

- The API would otherwise be welded to the schema, making every column rename a
  breaking change for the frontend.
- Internal fields would leak. `normalizedName` is a matching key, not
  information about the company, and publishing it invites clients to depend on
  normalization rules we intend to keep changing.
- Serialising a JPA entity triggers lazy loading during serialisation, which is
  how one request quietly becomes hundreds of queries.
- The request DTOs have no field for `id`, `slug`, `active` or the timestamps,
  which is the simplest possible defence against a client trying to set one.

Two response shapes exist because a search page does not need 2000-character
descriptions for records the user has not opened.

### Authentication

Implemented in-house with Spring Security, JWT and bcrypt. No paid identity
provider: authentication is a solved problem with well-understood primitives,
and the free-first rule means not renting one.

#### The flow

```
Registration                     Login
────────────                     ─────
POST /auth/register              POST /auth/login
  validate                         look up by normalized email
  normalize email                  verify password (bcrypt)
  hash password (bcrypt)           check active flag
  save user + empty profile        issue JWT
  201, no token                    200 + accessToken

Authenticated request
─────────────────────
Authorization: Bearer <jwt>
  → JwtAuthenticationFilter: verify signature, issuer, expiry
  → AuthenticatedUser(id, email, role) into the SecurityContext
  → authorization
  → controller reads the id from the context, never from the request
```

#### Registration does not issue a token

The client logs in afterwards with credentials it already has. One way to
obtain a token means one code path to audit; and email verification, the obvious
next requirement, sits exactly where auto-login would be. Not issuing a token
now means adding verification later does not have to take one away — which would
be a breaking change for the frontend.

#### Password hashing

A `DelegatingPasswordEncoder`, which stores the algorithm inside the hash
(`{bcrypt}$2a$10$...`). Moving to a stronger algorithm later is then a change of
default plus a re-hash on next login, rather than invalidating every password in
the database.

The work factor is left at the library default. Bcrypt is deliberately slow —
that is the point — and the free tier gives 0.1 CPU, so raising it would make
login painful for a marginal gain. Worth revisiting on real hardware.

Passwords are capped at 72 bytes because **bcrypt silently ignores everything
past 72**. A longer password would be accepted while part of it did nothing, so
a user could authenticate with a prefix of what they typed. The DTO limits
characters; the service checks UTF-8 bytes, since non-ASCII costs more than one
byte per character.

#### JWT design

```
sub    user id
email  so the principal can be built without a database read
role   so authorization works without a database read
iss    rejects correctly-signed tokens minted by something else
iat    issued at
exp    expiry
```

Nothing else. **A JWT is signed but not encrypted** — anyone holding it can read
every claim — so it carries the minimum needed to authorize a request and no
personal data beyond the email that identifies the account.

The secret comes from `JWT_SECRET` and has no default in `application.yml` or
the prod profile. A committed fallback is the classic way a JWT implementation
becomes forgeable: every deployment that forgot the variable would share a
secret that is also in public Git history. `JwtService` additionally refuses to
start if the secret is under 256 bits, because HS256 needs that much to be
meaningful. The dev and test profiles carry obviously-fake local values so that
`mvn spring-boot:run` needs no setup; they are written to be unusable rather
than plausible.

#### Stateless, and what that costs

Sessions are off entirely (`SessionCreationPolicy.STATELESS`). Identity comes
from the token on every request, which is what lets the backend scale to zero
and back without logging everyone out, and makes a second instance behave
exactly like the first.

Verifying a token touches no database. The principal is built from the claims.
Every authenticated request would otherwise pay for a query before doing any
work, which on 0.1 CPU is most of the request budget.

The honest cost: **a token stays valid until it expires, so deactivating an
account does not end a session already in progress.** The mitigation is the
one-hour lifetime. Immediate revocation needs a denylist or a per-request
lookup — a real feature with real infrastructure, not something to bolt on.

There is no refresh token in V1. A one-hour access token and a login form is a
complete story; refresh tokens bring rotation, reuse detection and revocation
with them.

#### CSRF is disabled, and that is correct here

CSRF protection defends against a browser attaching credentials to a request the
user did not intend. That requires *ambient* credentials — cookies or HTTP
basic. This API authenticates with an `Authorization` header that a client must
set deliberately, and a cross-site form post cannot set headers. With no session
cookie there is nothing to ride on.

This stops being true the moment a token goes into a cookie. That is the line to
watch, not the annotation.

#### Authorization

Default deny: `anyRequest().authenticated()`. Public routes are listed one by
one — the two auth endpoints, the health check, `/api/v1/meta`, and company
*reads*. Company *writes* now require authentication, tightening what step 3
left open. A new endpoint is protected until someone opens it deliberately,
because forgetting to protect something is silent and forgetting to open
something is immediately obvious.

Errors from the filter chain never reach `@RestControllerAdvice` — Spring
Security rejects those requests before any controller. So
`RestAuthenticationEntryPoint` and `RestAccessDeniedHandler` render 401 and 403
into the same `ApiError` shape, and a client only ever parses one error format.

CORS is expressed as a `CorsConfigurationSource` bean rather than through
`WebMvcConfigurer`, because Spring Security runs its own filter chain: a request
it rejects never reaches Spring MVC, so MVC-level CORS settings would not be
applied to a 401 and the browser would report an opaque CORS failure instead of
showing the client a readable error.

#### Not accepting a user id is the IDOR defence

Every user route is `/api/v1/users/me/...`. There is no `/users/{id}`, and no
request DTO has a `userId` field. The record being read or written is whichever
one the verified token points at.

This is deliberately structural rather than a check. A check can be forgotten on
one endpoint; a field that does not exist cannot be supplied on any of them.

### The User domain

```
users
  └── user_profiles (1:1, ON DELETE CASCADE)
        ├── user_skills
        ├── user_preferred_roles
        └── user_preferred_locations
```

Account and profile are separate because they change for different reasons and
at different rates. An account is identity and credentials; a profile is career
data that step 7 will match jobs against. Putting career fields on `users` would
mean loading a password hash every time the matcher wants a skill list.

A profile is created **with** the account, so every user always has one and no
endpoint has to handle a missing profile or risk creating a second. The `UNIQUE`
foreign key enforces one per user and `ON DELETE CASCADE` stops a profile
outliving its user, so there is no orphan to clean up.

The three preference tables are **element collections**, not entities. Each row
is a value with no identity or lifecycle of its own: it exists because the
profile says so and disappears with it. That gives cascade and orphan removal
for free, and keeps three tables from needing three entity classes, three
repositories and three sets of plumbing. Their primary key is
`(profile_id, normalized_*)`, which is both the natural key and the dedup rule.

Each value stores the text as typed plus a normalized form, produced by
`TextNormalizer` — the same rules the company domain uses. That shared
normalization is the point: a skill canonicalised one way here and another way
in the job domain would not join when step 7 tries to match them. It is also
what makes `Set` deduplication work, so adding `java` to a profile that already
lists `Java` is not a second skill.

No `@EntityGraph` fetches the three collections together: joining all of them in
one query produces a cartesian product — 50 skills, 20 roles and 20 locations
would return 20,000 rows for Hibernate to deduplicate in memory. Three extra
small queries inside the same transaction is the cheaper end of that trade.

Roles are a single column with two values, `USER` and `ADMIN`, and `ADMIN` is
never granted by any code path. A role column beats a permissions framework
until there is a second kind of user to justify one.

### The Job domain and job search

Jobs belong to a company and are the thing the product ultimately delivers.
Search over them is the most-used read path in JobLens, so it is worth being
explicit about how it works.

#### Why PostgreSQL is enough for V1

No Elasticsearch, no OpenSearch, no hosted search API. A search engine is a
second datastore to deploy, keep in sync, and pay for — and it earns that cost
when you need relevance ranking, fuzzy matching or millions of documents. V1
needs exact-ish substring matching over a few thousand rows, which PostgreSQL
does in milliseconds.

Adding one now would also mean the canonical data lives in Postgres while
search reads from a copy, and every write needs an indexing step that can fail.
That is a real distributed-systems problem taken on for no current benefit.

The honest limit: there is no relevance ranking and no fuzzy matching, so
`develper` finds nothing. When that stops being acceptable, PostgreSQL's own
full-text search (`tsvector` + GIN) is the next step, and a separate engine the
one after.

#### Filter architecture

```
JobController            binds and validates request parameters
      │                  builds Pageable via JobSortParser (allowlist)
      ▼
JobSearchCriteria        one value holding every filter; null = don't filter
      │                  validate() covers the cross-field rules
      ▼
JobSpecifications        criteria -> JPA predicates
      │
      ▼
JobRepository            JpaSpecificationExecutor + @EntityGraph
      │
      ▼
PostgreSQL               filters, sorts and pages
```

`Specification` is used because there are ten optional filters. The alternative
— one JPQL query with `(:param IS NULL OR column = :param)` repeated ten times —
works, but it is unreadable at this size and makes PostgreSQL plan conditions
the caller did not ask for. Specifications are also just Spring Data: no query
framework of our own, and each filter is a five-line function.

A criteria record exists for the same reason: a repository method with ten
parameters cannot be called correctly, and grows another parameter with every
filter added.

Boolean structure is deliberate and stated in one place:

```
search AND companyId AND location AND employmentType AND workMode
       AND experience AND active AND dates

search itself:  title LIKE %term%  OR  description LIKE %term%
```

Each filter narrows, so they AND; the keyword ORs across two columns because
they are two places the same word appears.

#### Everything happens in the database

No code path loads jobs to filter them in Java. Predicates become SQL, `LIMIT`
and `OFFSET` are applied by PostgreSQL, and `totalElements` comes from a count
query — not from the size of a list we fetched.

Case-insensitivity is `LOWER(column) LIKE LOWER(pattern)`, which is portable and
obvious. User-supplied `%` and `_` are escaped, so a search term cannot act as a
wildcard or force pathological backtracking. (Company search does not need this
because its terms pass through normalization, which strips those characters
entirely; job search matches raw text and must escape explicitly.)

#### Experience overlap

Both the job and the query describe a range, either end of which may be open,
and a job matches when the ranges overlap:

```
match iff  (jobMin ?? 0) ≤ (searchMax ?? ∞)  AND  (jobMax ?? ∞) ≥ (searchMin ?? 0)
```

Each predicate is written as "unspecified OR within bound", so **a job with no
stated experience is never filtered out**. An employer who left it blank has not
said "zero years"; treating null as 0 would quietly drop those jobs from every
experience-filtered search — the class of bug where users decide search is
broken but cannot say why.

#### Pagination and sorting

`Pageable`, built in the controller rather than bound automatically. Page size
defaults to 20 and is capped at 50: without a ceiling, `?size=1000000` is a free
denial-of-service against a 512 MB container.

Sort fields are an allowlist (`postedAt`, `createdAt`, `title`). Letting a
client sort by any mapped property means sorting by unindexed columns and
turning a typo into a `PropertyReferenceException` and a 500.

Sorting is always stabilised with `id` as a tiebreaker. Without one, two jobs
posted in the same second can swap places between page 1 and page 2, so a row
appears twice and another is never seen — invisible with tidy test data,
obvious in production.

Default order is newest first: a stale opening is worse than a less relevant
one, and there is no relevance score to sort by yet.

#### N+1 prevention

Every result row shows its company's name and slug, and `Job.company` is
`LAZY` — so one query for a page of 20 jobs would be followed by up to 20 more.

The fix is a re-declared `findAll(Specification, Pageable)` on the repository
carrying `@EntityGraph(attributePaths = "company")`, which fetch-joins the
company in the same query. This is safe **because the association is
to-one**: fetch joining a *collection* alongside pagination forces Hibernate to
load every row and paginate in memory. A to-one join multiplies no rows, so
`LIMIT`/`OFFSET` still work in SQL.

Rejected alternatives: making the association `EAGER`, which would drag a
company into every query touching a job whether or not anyone wanted it; and a
DTO projection, which is faster still but adds a second mapping to keep in step
with the entity.

#### Indexes

| Index | Why |
| ----- | --- |
| `ix_jobs_company_id` | PostgreSQL does not index a foreign key automatically. Serves the `companyId` filter and referential checks. |
| `ix_jobs_active_posted_at (active, posted_at DESC, id)` | Serves the default query — active jobs, newest first — so the database finds rows already in order and stops after one page. Equality column first, ordering column second; reversed it would not serve the filter. |

Deliberately **not** indexed:

- `employment_type`, `work_mode` — six and three values. An index whose every
  entry matches a large slice of the table cannot narrow enough to beat a scan;
  the planner will usually ignore it while writes still pay to maintain it.
- `title`, `description`, `location` — search is `LOWER(col) LIKE '%term%'`, and
  a B-tree can serve neither a leading wildcard nor `LOWER(col)`. A plain index
  would be pure overhead. Making these fast needs trigram indexing
  (`CREATE EXTENSION pg_trgm` + a GIN index), which is written down in
  `V5__add_job_search_indexes.sql` but not done: it needs an extension a managed
  free tier may not allow, it costs real write time and disk, and at V1 volumes
  the sequential scan is measured in milliseconds. When that stops being true it
  is a migration, not a rewrite.

### Matching

```
          User Profile
                │
   ┌────────────┼────────────┬───────────┬────────────┐
 Skills    Experience     Roles     Locations    Work mode
   │            │            │           │            │
   └────────────┴─────┬──────┴───────────┴────────────┘
                      ▼
              Matching Engine          (pure, no database)
                      │
                      ▼
                    Job
                      │
                      ▼
            Score + Breakdown + Explanation
```

#### Engine and service are separate, deliberately

`JobMatchingEngine` holds the rules and touches no database, no clock and no
randomness. `JobMatchingService` touches the database and holds no rules. The
engine takes `MatchProfile` and `MatchJob` value objects rather than entities,
so the scoring rules can be tested in milliseconds and reviewed without reading
a persistence layer — and so the same inputs always give the same score.

#### No AI, on purpose

Every rule is arithmetic a person can read and disagree with. A model would
probably rank better on average and would not be able to say why; "why did this
job score 72?" is a question this product exists to answer. AI-assisted matching
is a later step and will sit beside this rather than replace it.

#### The formula

```
for each of the five criteria:
    if there is enough data on both sides:
        earned    += fraction(0..1) × weight
        available += weight

score = round(earned / available × 100)
```

| Criterion | Weight | Why |
| --------- | -----: | --- |
| Skills | 50 | The only criterion about capability. Worth as much as the other four together. |
| Experience | 20 | A real signal but a crude one — two years somewhere demanding beats five somewhere idle. |
| Location | 15 | A hard blocker in practice, but about circumstances rather than suitability. |
| Role | 10 | Titles are inconsistent between companies. Low weight reflects low trust. |
| Work mode | 5 | Real but narrow: three values, and hybrid is partly compatible with both others. |

All five live in `MatchingWeights`, bound from `joblens.matching.weights.*`.
Numbers scattered through scoring code are impossible to review or tune, and
turn "why did this score 72?" into archaeology.

#### Missing data is excluded, not scored zero

A criterion that cannot be judged drops out of the total **and takes its weight
with it**. A user who has not listed preferred locations has not failed a
location test; they have not taken one. Scoring them zero would punish an
incomplete profile and make every score incomparable with every other.

The cost, stated: two scores are strictly comparable only when computed over the
same criteria, so every response reports which applied, and the explanation says
so in words. If nothing can be judged the result is `scored: false` rather than
0 — "we cannot tell" and "terrible match" lead a user to do different things.

#### The rules

- **Skills** — fraction of the job's skills the user holds. Extra skills the job
  does not ask for are neither rewarded nor penalised. Not judged if either side
  has none.
- **Experience** — full inside the range; `1 − shortfall/3` below the minimum,
  reaching zero at three years short; `1 − 0.1 × excess` above the maximum, never
  below half. Asymmetric because being under-qualified is an obstacle and being
  over-qualified is not.
- **Location** — whole-word matching, so `pune` matches `pune maharashtra india`
  but not `punegar`. Preferring `remote` matches a remote job whatever its
  address says. Binary; real distance is a later step.
- **Role** — fraction of the user's preferred-title words present in the job
  title, best of several. Extra title words are free, so "Senior" does not spoil
  a match. Below half is treated as coincidence, because nearly every
  engineering title shares "developer" with nearly every other.
- **Work mode** — exact 1.0, anything involving hybrid 0.5, remote against
  onsite 0.0. `ANY` means no preference stated and is not judged.

#### The shared skill vocabulary

Skills became their own table in step 7 (`V6`), with `job_skills` and
`user_profile_skills` pointing at it. Previously a profile's skills were private
free text, which was right when nothing else had skills and wrong the moment
matching had to ask whether a job's "Java" and a user's "Java" are the same
thing. Two independent text columns can only be compared by string equality and
drift apart forever; a shared row is an identity.

Identity is `normalized_name`, from the same `TextNormalizer` used for company
names and job titles, so "Spring Boot", "spring boot" and "SPRING  BOOT"
converge. `SkillService` does find-or-create in two queries however many names
are supplied, and tolerates the race on the unique index — two people typing
"Kubernetes" at once is not an error.

The vocabulary is open rather than curated: a user may name a skill nobody has
used before. The cost is near-duplicates ("NodeJS" vs "Node.js"), accepted for
V1. A synonym table is a later decision with evidence behind it.

Deliberately not an ontology — no categories, no parent/child, no proficiency.

Preferred roles and locations stayed as value collections, and the difference is
the point: a skill is something jobs also reference, so it needs identity. A
preferred role is one person's wording of a preference with no counterpart
table.

#### Recommendations, and their limits

`GET /jobs/recommended` scores **the 200 most recent active jobs**, sorts by
score then posting date then id, and pages the result in memory. One
page-sized query plus one skills query, whatever the table grows to.

The limitation is real: a strong match outside that window is not found, and
`totalElements` counts candidates rather than all jobs. Fixing it means
narrowing candidates by something better than recency — jobs sharing at least
one of the user's skills, which `ix_job_skills_skill_id` already supports — or
precomputing scores. Worth doing when there are enough jobs for the window to
bite; neither needs Redis or a recommendation service.

Paging happens in memory because the ordering is per-user and cannot be
expressed as SQL ordering without storing scores. The candidate cap is what
keeps that honest — the list being sliced is at most 200 entries.

#### N+1 prevention

Two places would have produced one, and both are handled:

- **Company per job** — the candidate query uses `findByActiveTrue` with
  `@EntityGraph(attributePaths = "company")`, safe because the association is
  to-one.
- **Skills per job** — `findSkillsForJobs` returns every (job, skill) pair for
  the candidate set in a single flat projection, grouped in memory. Fetch
  joining the collection alongside pagination would multiply rows and force
  in-memory paging; lazy loading inside the scoring loop would be 200 queries.

A single match uses `findWithCompanyAndSkillsById`, one query for both.

#### Security

Both endpoints require authentication and neither accepts a user id — the
profile is always the token's, so there is nothing to tamper with.

One ordering detail worth knowing: `GET /api/v1/jobs/**` is public for
discovery, so `/jobs/recommended` and `/jobs/*/match` are listed **before** it in
`SecurityConfig` and marked `authenticated()`. Security rules are evaluated in
order and the first match wins; listed afterwards, a user's match results would
become an anonymous read. There is a test for exactly that.

### API versioning

Every endpoint is versioned from the first one: `/api/v1/...`, built from the
`ApiRoutes.API_V1` constant so the prefix appears in exactly one place.

```
/api/v1/auth         step 4
/api/v1/users        step 4
/api/v1/companies    step 3
/api/v1/jobs         step 5
/api/v1/scans        step 11
```

Conventions: plural nouns for collections, HTTP verbs for actions, list
endpoints return a `PageResponse` envelope rather than Spring Data's `Page`
(whose serialised form is unstable and leaks internals). A breaking change means
`/api/v2`, with v1 kept alive until nothing uses it.

`/api/v1/meta` exists today and reports the application name, version and
environment. Operational health stays on `/actuator/health`, so internal health
detail is never exposed to a browser.

### Testing strategy

Four tiers, cheapest first:

| Tier              | Annotation                      | Scope |
| ----------------- | ------------------------------- | ----- |
| Unit              | none                            | Plain JUnit, no Spring. The default for business logic. |
| Controller slice  | `@WebMvcTest`                   | Web layer only, service beans mocked. No database. |
| Repository slice  | `@DataJpaTest`                  | Queries and mappings against real PostgreSQL. |
| Integration       | `@IntegrationTest` (custom)     | Full context, real database. |

`@IntegrationTest` is a meta-annotation over `@SpringBootTest` +
`@ActiveProfiles("test")`, defined once so configuration cannot drift between
test classes and so Spring caches and reuses a single context across them.

Tests run against real PostgreSQL rather than an in-memory database, because an
in-memory database behaves differently from the thing we deploy -- which is
precisely what an integration test is supposed to catch.

## Frontend architecture

```
React
  │
  ├── Router            app/router.tsx — public and protected route groups
  │
  ├── Feature modules   features/{auth,jobs,companies,matching,profile,scan}
  │                     each owning its pages and its types
  │
  ├── Shared components components/{layout,ui}
  │
  └── API services      services/api — one HTTP client, one error type,
          │             one list of endpoint paths
          ▼
     Spring Boot
```

### Feature-oriented, not layer-oriented

```
src/
├── app/          root component, router, error boundary, non-feature pages
├── components/   layout shell and shared UI
├── features/     one folder per domain, owning its pages and types
├── lib/          environment configuration
├── services/     api client, token storage
├── types/        shapes shared across features
└── test/         Vitest setup
```

The same reasoning as the backend's packages: a feature lives in one place, so a
change to job search touches `features/jobs/` rather than being spread across a
`components/`, a `pages/` and a `types/` directory. The rule for promoting
something into `components/` is that more than one feature uses it — a component
used once belongs with its page.

### Routing

`createBrowserRouter`, with every route nested under one layout so the shell is
defined once. Routes are grouped into public (`/`, `/login`, `/register`) and
protected (`/dashboard`, `/jobs`, `/jobs/:jobId`, `/companies/:companyId`,
`/profile`, `/scan`).

The protected group sits under a `ProtectedRoutes` element that currently
renders its children unchanged. The guard is one line and the group is the place
it goes — but adding it before sign-in works would lock everyone out of the only
pages that could test it. The grouping also makes "which pages need an account?"
answerable from the route table.

**This is a convenience, not a security boundary.** The backend rejects
unauthenticated requests itself; hiding a route in the browser stops nothing.

Planned and documented, not yet routed: `/tracked-companies` (step 10) and
`/jobs/recommended` (step 9). The latter must be declared *before*
`/jobs/:jobId`, or "recommended" is read as an id.

### API client

Every request goes through `services/api/client.ts`. That is what lets the base
URL, the `Authorization` header, JSON encoding and error translation be decided
once; a feature that calls `fetch` directly loses all four.

`services/api/endpoints.ts` holds the paths, so a renamed route is one edit and
the file doubles as the documented contract between the two halves of the
project.

Failures throw `ApiError` carrying `status`, a stable `code`, a `message`,
field-level `details` and the `traceId` that also appears in the backend logs.
It handles the awkward cases deliberately: a rejected `fetch` (offline, DNS,
CORS, or a sleeping free-tier backend — the browser does not say which), a 204
with no body, and an error response that is not JSON at all.

**No data-fetching library.** TanStack Query brings caching, retries and
deduplication, none of which this application needs yet — there are no pages
that fetch the same thing twice. It can wrap these functions later without
feature code changing.

### TypeScript models

Types mirror the backend DTOs field for field, taken from the source rather than
assumed. They describe the *API contract*: fields the server has but does not
expose — normalized names, entity versions — are deliberately absent.

Two corrections worth recording, because the obvious guesses are wrong:

- The job field is **`applyUrl`**. There is no `applicationUrl` and no `source`
  field; aggregating external job sources is a V2 concern.
- There is **no `/companies/{id}/jobs` endpoint.** A company's openings come
  from `/jobs?companyId=…`, the same search endpoint with one more filter — so
  that list gets pagination, sorting and every other filter for free.

`CriterionScore.applicable: false` is also worth calling out on the frontend: it
means the criterion was excluded from the total along with its weight, not that
it scored zero. `maxScore` is then 0, and the UI must show the reason rather
than a 0/50 bar — the user has not failed anything.

### State management

React state and Context. No Redux, no Zustand, no store.

There is currently no state shared between distant parts of the tree. The one
candidate is the signed-in user, which is a small Context when authentication is
wired up. A store adopted before there is shared state to put in it is a set of
conventions without a problem, and it is far easier to add one later than to
unpick one that was never needed.

### Environment configuration

`VITE_API_BASE_URL` is the backend **origin** (`http://localhost:8080`), not the
API root. The `/api/v1` prefix lives in code, because the API version belongs to
the contract rather than to the deployment — moving to `/api/v2` is then a code
change, not a reconfiguration of every environment.

A missing value does not throw during module initialisation. That renders a
blank page with the reason buried in the console, which is the worst possible
failure on a freshly configured deployment; it is reported in the UI instead.

**Anything prefixed `VITE_` is compiled into the bundle and readable by anyone.**
No secret, password, signing key or private API key may ever go behind that
prefix.

### Token storage

Held in memory and mirrored to `sessionStorage`. In-memory alone logs the user
out on every refresh; `localStorage` persists across browser sessions and is
readable by any injected script. `sessionStorage` survives a refresh and dies
with the tab.

None of these resist XSS. The only storage that does is an httpOnly cookie,
which would mean switching the backend from header authentication to cookies and
taking on CSRF protection with it — a real decision, not something to do in
passing. Recorded here as the thing to revisit.

### Accessibility

Treated as part of building a component, not a later cleanup: real `<button>`
elements, labels tied to inputs with `useId`, `aria-invalid` and
`aria-describedby` on errors, a visible `:focus-visible` ring everywhere, a skip
link, `role="status"` on the spinner, `role="alert"` on error messages, and
`prefers-reduced-motion` respected. Colour never carries meaning alone — badges
take an optional screen-reader label for exactly that reason.

### Vercel deployment

Static build to `dist/`, Root Directory `frontend`. `vercel.json` rewrites all
paths to `index.html`, which client-side routing needs — without it, reloading
`/jobs/123` returns a CDN 404. No backend URL is hard-coded anywhere.

## Frontend / backend relationship

Strictly separated. The React application is a static bundle; it holds no
server-rendered state and never talks to the database. All communication is
JSON over HTTP against versioned `/api/v1/**` endpoints.

- The backend is the only source of truth; the frontend renders what it is given.
- Every network call goes through one wrapper (`frontend/src/lib/api.ts`), so
  the base URL, headers, auth token and error translation live in a single place.
- The API base URL is an environment variable. No production hostname is ever
  hard-coded.
- Failures use one error shape (`ApiError`: timestamp, status, code, message,
  path, field details), produced centrally by `GlobalExceptionHandler`.

## Database responsibility

PostgreSQL is the single system of record. Nothing else stores durable state.
In particular the AI service is stateless and never connects to the database.

The schema is owned exclusively by **Flyway**. Migrations are forward-only,
versioned, committed to the repository, and applied automatically at startup.
Schema is never changed by hand or generated by an ORM at runtime
(`ddl-auto` is never used). `V1__baseline.sql` establishes Flyway ownership;
business tables arrive in their own numbered migrations as each domain is built.

## Future AI service

The one boundary V1 will split out is OCR/AI, as a stateless Python FastAPI
service called synchronously by the backend over HTTP. The browser never calls
it directly; the backend owns the request, the verification of the result
against real company records, and everything persisted. Rationale and the
planned contract are in [ai-service/README.md](ai-service/README.md).

## Future evolution path

Change is driven by measured pain, not anticipation:

1. **Now** - modular monolith + PostgreSQL.
2. **OCR step** - add the Python AI service behind a synchronous HTTP call.
3. **If reads get slow** - add indexes and query tuning first; caching only
   after profiling shows where the time goes.
4. **If scan latency becomes unacceptable** - make the scan asynchronous
   (job + polling), before reaching for a broker.
5. **If a module genuinely needs independent scaling or deployment** - extract
   that package, which is already a clean boundary.

Redis and Kafka are deliberately absent. Neither solves a problem JobLens has
today, and both would add infrastructure and operational weight for no current
benefit.

## Free-first deployment philosophy

JobLens is a portfolio project, so recurring cost is a real design constraint.

- Prefer open-source and free tiers; no paid infrastructure or paid APIs
  without explicit approval.
- Prefer boring, portable technology. Postgres, plain HTTP and Docker run
  everywhere, so no provider can lock us in. If a free tier disappears we move.
- Keep the number of deployable units small. Every additional service is
  another free-tier allowance to spend.
- Keep configuration in environment variables so the same artifact runs locally
  and on any host.
- Free tiers change. Verify what is actually available at the moment we deploy
  rather than trusting the plan written today.
