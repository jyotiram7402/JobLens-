# TODO

Working checklist. Step numbers refer to [ROADMAP.md](ROADMAP.md).

## Completed

### Step 1 - Foundation

- [x] Monorepo structure (`backend`, `frontend`, `ai-service`, `docker`, `docs`)
- [x] `.gitignore` covering Java, Node, Python, IDE and secrets
- [x] Spring Boot 3.3 / Java 21 Maven project with a minimal dependency set
- [x] Application entry point and package layout (`config`, `common`, `health`)
- [x] Environment-based PostgreSQL configuration, no committed credentials
- [x] Flyway configured with a `V1__baseline.sql` migration
- [x] Actuator health endpoint, `/api/v1/meta` endpoint
- [x] Uniform API error shape and global exception handler
- [x] CORS configuration driven by environment variable
- [x] Spring context smoke test plus `test` profile
- [x] React 18 + TypeScript + Vite frontend with routing and a layout shell
- [x] Centralised API client and environment-based API base URL
- [x] Home page that reports backend connectivity
- [x] `docker/docker-compose.yml` for local PostgreSQL
- [x] GitHub Actions CI workflow (backend build/test, frontend install/build)
- [x] README, ARCHITECTURE, ROADMAP, TODO, `docs/daily/day-01.md`, ADR 0001

### Step 1b - Verification scaffolding

Since nothing can be run locally, CI and the deployed environments are the only
proof the code works. This made them a prerequisite, not a final step.

- [x] CI frontend job no longer requires a committed `package-lock.json`
- [x] Frontend type-check added to CI
- [x] Missing `VITE_API_BASE_URL` reports itself in the UI instead of rendering
      a blank white page
- [x] API client distinguishes unreachable/CORS failures from HTTP errors
- [x] Home page shows an explicit environment-check panel
- [x] Backend honours the `PORT` variable injected by PaaS providers
- [x] `DB_URL_PARAMS` for provider-specific JDBC options (e.g. `sslmode`)
- [x] `backend/Dockerfile` (multi-stage, non-root)
- [x] `render.yaml` blueprint for backend + PostgreSQL
- [x] `frontend/vercel.json` SPA rewrite
- [x] `docs/VERIFICATION.md` and `docs/DEPLOYMENT.md`
- [x] Verified current Render / Vercel / Neon free-tier terms (2026-09-22) and
      moved the database to Neon, because Render's free PostgreSQL expires
      after 30 days
- [x] JVM flags tuned for a 512 MB / 0.1 CPU free container
- [x] CI triggers on `master` as well as `main`, so a push actually runs it

### Step 2 - Backend architecture

- [x] Domain-oriented package structure under `com.joblens.api`
      (`config`, `common`, and boundary-only `user`/`company`/`job`/`matching`/
      `tracking`/`scan`/`notification` packages)
- [x] Module rules written down: `common` depends on nothing; modules talk
      through services, never another module's repository
- [x] Swapped the JDBC starter for Spring Data JPA
- [x] JPA configured: `ddl-auto: validate`, `open-in-view: false`, UTC
      timestamps, auditing enabled
- [x] `BaseEntity` mapped superclass: id, audit timestamps, optimistic lock
- [x] `ErrorCode` enum and `ApplicationException` hierarchy
      (`ResourceNotFoundException`, `DuplicateResourceException`)
- [x] `GlobalExceptionHandler` extending `ResponseEntityExceptionHandler`, so
      Spring's own MVC errors return the same body
- [x] `ApiError` response with `traceId`, and field-level validation details
- [x] `PageResponse` envelope for future list endpoints
- [x] `CorrelationIdFilter` -> MDC, response header, and error body
- [x] Log pattern carrying `%X{traceId}`; SQL logging on in dev only
- [x] Profile strategy: `application.yml` + `dev` / `prod` / `test`, with no
      fallbacks at all on `prod`
- [x] Typed, validated `CorsProperties`; no wildcard origin; Actuator excluded
      from CORS
- [x] `ApiRoutes.API_V1` constant; `/api/v1/meta` now reports the environment
- [x] Test tiers established: plain unit, `@WebMvcTest` slice, and an
      `@IntegrationTest` meta-annotation for full-context tests
- [x] Render blueprint sets `SPRING_PROFILES_ACTIVE=prod`

### Step 3 - Database + Company

- [x] Identity strategy decided and documented: time-ordered UUID primary keys
      (`BaseEntity` switched from `Long`/IDENTITY before any entity existed)
- [x] Timestamp strategy: `Instant` stored as `timestamptz`, always UTC
- [x] `V2__create_companies_table.sql` with constraints, unique indexes and
      column comments
- [x] `Company` entity with no public setters and no direct normalized-name access
- [x] `CompanyRepository`: by id, by slug, by normalized name, paged search
- [x] `CompanyService`: creation, retrieval, update, search, duplicate detection
- [x] DTO records: `CreateCompanyRequest`, `UpdateCompanyRequest`,
      `CompanyResponse`, `CompanySummary` — entities never leave the service
- [x] `CompanyNameNormalizer`: deterministic, documented rules, Unicode-safe
- [x] `SlugGenerator`: stable slugs with `-2`/`-3` collision suffixes
- [x] Domain error codes `COMPANY_NOT_FOUND` and `COMPANY_ALREADY_EXISTS`
- [x] `CompanyController`: create (201 + Location), search, get by id,
      get by slug, update
- [x] Validation on every field, with `http`/`https`-only URLs
- [x] Page size capped at 50
- [x] Dev-only seed data under `db/seed`, loaded by the dev profile alone
- [x] Tests: normalizer, slug generator, service (Mockito), repository
      (`@RepositoryTest`), controller (`@WebMvcTest`)
- [x] `docs/api/companies.md` with full request/response examples
- [x] `docs/daily/README.md` index, so day numbers and step numbers stop being
      confused with each other

### Step 4 - Authentication + User profile

- [x] `V3__create_users_and_profile_tables.sql`: `users`, `user_profiles`,
      `user_skills`, `user_preferred_roles`, `user_preferred_locations`
- [x] `User` entity with a password *hash* only, and a `toString` that cannot
      leak it
- [x] `UserProfile` with the three preference collections as JPA element
      collections, deduplicated by normalized form
- [x] `Role` enum (`USER`, `ADMIN`) as a column, not a permissions framework
- [x] `TextNormalizer` moved into `common` so `user` and `company` share one set
      of rules — step 7 needs them to join
- [x] `POST /auth/register` with validation, duplicate detection and an empty
      profile created alongside the account
- [x] `POST /auth/login` returning `accessToken`, `tokenType`, `expiresIn`, user
- [x] Bcrypt via `DelegatingPasswordEncoder`; 72-byte password cap
- [x] `JwtService`: issue, verify, minimal claims, issuer check, startup failure
      on a secret under 256 bits
- [x] `JwtAuthenticationFilter`, stateless, with no database read per request
- [x] `SecurityConfig`: stateless sessions, CSRF off (header auth, no cookie),
      default-deny authorization, company writes now authenticated
- [x] `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` so 401 and 403
      use the same `ApiError` shape as everything else
- [x] CORS moved to a `CorsConfigurationSource` bean so Security applies it
- [x] `GET /users/me`, `GET`/`PUT /users/me/profile` — no `userId` anywhere
- [x] Login does not reveal whether email or password was wrong, and takes the
      same time either way
- [x] `JWT_SECRET` / `JWT_EXPIRATION` wired through `.env.example` and
      `render.yaml`; no secret committed
- [x] Tests: `JwtServiceTest`, `AuthServiceTest`,
      `AuthAndProfileIntegrationTest`
- [x] `docs/api/auth-and-users.md`

### Step 5 - Jobs

- [x] `V4__create_jobs_table.sql`, with `ON DELETE RESTRICT` to the company,
      CHECK constraints on the enums and the experience range, and an index on
      the foreign key
- [x] `Job` entity with no public setters, `EmploymentType`, `WorkMode`
- [x] `JobRepository`, `JobService`, `JobController`
- [x] DTO records: create, update, `JobResponse`, `JobSummary`, `JobCompanyRef`
- [x] Public reads, authenticated writes; `POST /{id}/close` rather than DELETE
- [x] `normalized_title` stored for step 7 matching
- [x] Dev-only seed data written to exercise search, not to look tidy

### Step 6 - Job search and filtering

- [x] `JobSearchCriteria` — ten optional filters in one value, with the
      cross-field rules Bean Validation cannot express
- [x] `JobSpecifications` — dynamic predicates, all filtering database-side
- [x] Keyword search over title OR description, case-insensitive, with user
      wildcards escaped
- [x] Company, location, employment type, work mode, active and date filters
- [x] Experience matching by range overlap; jobs with no stated experience are
      never excluded
- [x] `active=true` default; `active=false` requires authentication
- [x] `JobSortParser` — allowlisted sort fields with `id` appended as a stable
      tiebreaker
- [x] Pagination capped at 50 per page
- [x] N+1 prevented with `@EntityGraph` on the search query
- [x] `PageResponse` gained `hasNext` / `hasPrevious`
- [x] `V5__add_job_search_indexes.sql`, with the rejected indexes documented
      and `pg_trgm` written down but not adopted
- [x] Tests: repository search, sort parser, criteria validation, controller,
      service
- [x] `docs/api/jobs.md`

### Step 7 - Job matching engine

- [x] `V6__create_skills_tables.sql`: shared `skills` vocabulary, `job_skills`,
      `user_profile_skills`, backfilled from `user_skills`, which it drops
- [x] `skill` module — its own, because both `user` and `job` reference it
- [x] `SkillService` find-or-create: two queries for any number of names, and
      tolerant of the race on the unique index
- [x] `UserProfile.skills` and `Job.skills` both point at the shared table
- [x] Jobs accept and return skills, so `job_skills` can be populated through
      the API rather than only by seed data
- [x] `MatchingWeights` — every number in one configurable place
- [x] `JobMatchingEngine` — pure, deterministic, no database, no AI
- [x] Skill, experience, location, role and work-mode rules, each documented
- [x] Missing criteria excluded from the total rather than scored zero, and
      "nothing judgeable" reported as unscored rather than 0
- [x] `MatchExplanationWriter` — sentences generated from the outcome, never
      claiming a skill the user lacks
- [x] `GET /jobs/{jobId}/match` and `GET /jobs/recommended`, both authenticated,
      neither accepting a user id
- [x] Security rule ordering so the matching routes are not swallowed by the
      public `GET /jobs/**` rule
- [x] N+1 prevented twice: entity graph for companies, flat projection for job
      skills
- [x] Tests: engine rules, explanation, security and end-to-end matching
- [x] `docs/api/matching.md`

### Step 8 - React frontend foundation

- [x] Feature-oriented structure: `app/`, `components/`, `features/`, `lib/`,
      `services/`, `types/`
- [x] Routing with public and protected groups, and the one place the auth
      guard will go
- [x] `services/api/client.ts` — GET/POST/PUT/DELETE, bearer header, query
      building that drops unset filters, 204 handling, network-failure handling
- [x] `services/api/endpoints.ts` — every backend path in one place
- [x] `ApiError` with status, stable code, message, field details and `traceId`
- [x] `tokenStorage` — minimal, `sessionStorage`-backed, trade-off documented
- [x] TypeScript models mirroring the real DTOs: auth, profile, company, job,
      match, `PageResponse`, `ApiErrorBody`
- [x] Shared UI: Button, Input, Select, Card, Badge, Spinner, LoadingState,
      ErrorState, EmptyState
- [x] Layout shell with responsive navigation and a skip link
- [x] Accessibility built in: real buttons, `useId` label association,
      `aria-invalid`/`aria-describedby`, focus ring, `prefers-reduced-motion`,
      colour never the only signal
- [x] `ErrorBoundary`, so one broken component cannot blank the page
- [x] Page shells for home, login, register, dashboard, jobs, job detail,
      company detail, profile and scan
- [x] Home page keeps the deployment environment check
- [x] Vitest + Testing Library, with component, page and utility examples
- [x] `npm test` added to CI
- [x] `frontend/README.md`; `.env.example` documents origin-not-API-root

### Step 9 - Dashboard and the company/job UI

- [x] `AuthContext` — signed-in user, session restore, login, register, logout
- [x] Login and registration wired to the API, with field-level errors
- [x] `RequireAuth` guard that waits for the session check before redirecting
- [x] Profile editing against `PUT /users/me/profile`
- [x] Dashboard: greeting by name, derived statistics, recommendations with
      scores, recent jobs
- [x] Job search: keyword with debounce, location, employment type, work mode,
      experience and sort — all in the URL, all applied by the backend
- [x] Pagination that preserves the filters
- [x] `JobCard`, shared by search, dashboard and the company page
- [x] Job detail: description rendered as text, details, apply link with
      `rel="noopener noreferrer"`, company brief
- [x] `MatchPanel`: score, per-criterion table, matched and missing skills, the
      backend's explanation — and a sensible fallback for every failure mode
- [x] Company detail with its openings, via `/jobs?companyId=`
- [x] `useAsync` and `useDebouncedValue`
- [x] `Avatar` with generated initials; `Pagination`
- [x] Loading, error and empty states on every data-backed view
- [x] Responsive layout with a mobile navigation toggle
- [x] Tests: JobCard, JobsPage, JobDetailPage, CompanyDetailPage, DashboardPage

## Current

Closing the Step 1 verification loop — see
[docs/VERIFICATION.md](docs/VERIFICATION.md).

Setup instructions: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

- [ ] Push to GitHub and get **CI green** (backend + frontend jobs)
- [ ] Create the Neon project; collect the five `DB_*` values
- [ ] Create the Render blueprint; confirm `/actuator/health` returns `UP`
- [ ] Confirm `flyway_schema_history` shows `V1` applied successfully
- [ ] Deploy the frontend to Vercel with root directory `frontend` and
      `VITE_API_BASE_URL` set
- [ ] Set Render `CORS_ALLOWED_ORIGINS` to the Vercel origin and redeploy
- [ ] Confirm the environment-check panel reports `API reachable: yes`
- [ ] Confirm a deep-link reload renders the app 404, not a CDN 404
- [ ] Decide whether to rename `master` to `main`
- [ ] Once `npm install` has run somewhere permitted, commit
      `package-lock.json` and switch CI back to `npm ci` + npm cache
- [ ] Confirm the pinned Spring Boot, React, Vite and Node versions resolve

## Next

### Step 10 - Tracking

- [ ] Track and untrack companies and jobs
- [ ] Application status per tracked job
- [ ] Tracking views, and the dashboard's "Tracked companies" tile made real
- [ ] A `/tracked-companies` route

### Deferred from Step 9

- [ ] A company search/browse page — only company *detail* exists, reachable
      from a job
- [ ] Tag inputs for skills, roles and locations instead of comma-separated text
- [ ] Skeleton loaders in place of spinners on the list pages
- [ ] Match scores on the job search results, which would need either a bulk
      match endpoint or one request per card
- [ ] Remember the last search when returning to `/jobs` from a job

### Deferred from Step 8

- [ ] Revisit token storage if XSS risk justifies httpOnly cookies, which would
      mean cookie auth and CSRF protection on the backend
- [ ] A data-fetching library, once a page genuinely refetches the same data
- [ ] ESLint and Prettier — no linting is configured anywhere in the project yet
- [ ] A real favicon and social preview image

### Deferred from Step 7

- [ ] Narrow recommendation candidates by shared skills instead of recency, so
      a good older match is not missed
- [ ] Skill synonyms ("NodeJS" vs "Node.js") — needs evidence of the problem
      before it needs a table
- [ ] Nice-to-have vs required skills on a job, with a scoring rule to match
- [ ] Match scores on the job search response, if the frontend wants them there
- [ ] Score labels ("Strong Match"), only as documented score ranges

### Deferred from Step 6

- [ ] `pg_trgm` GIN indexes on title/description once job volume justifies them
- [ ] Relevance ranking — currently results are ordered by date, not by how well
      they match the keyword
- [ ] Fuzzy matching, so `develper` finds something
- [ ] Salary range on jobs, and filtering by it
- [ ] Structured location (country/city) instead of free text
- [ ] `GET /api/v1/companies/{id}/jobs` if the frontend wants it; `companyId`
      on job search covers it for now

### Deferred from Step 4

- [ ] Email verification on registration (the reason registration issues no
      token)
- [ ] Refresh tokens, if a one-hour session proves too short in practice
- [ ] Token revocation / denylist, so deactivating an account ends live sessions
      rather than waiting for expiry
- [ ] Rate limiting on `/auth/login` and `/auth/register`
- [ ] `PATCH /users/me` for changing name, and a password-change endpoint

### Deferred from Step 3

- [ ] `PATCH /api/v1/companies/{id}/active` to hide a company from search,
      once there is an authenticated user allowed to press it
- [ ] Client-configurable sorting on company search (step 6, with an allowlist)
- [ ] Replace `LIKE '%term%'` with a `pg_trgm` GIN index (step 6)

## Future

Steps 4 to 15 in [ROADMAP.md](ROADMAP.md): authentication, jobs, search,
matching, frontend, dashboard, tracking, scan, OCR/AI, AI job intelligence,
production readiness, deployment. V2/V3 items are out of scope.
