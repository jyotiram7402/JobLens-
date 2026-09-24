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

### Step 4 - Authentication + User

- [ ] `V3__create_users_table.sql`
- [ ] `User` entity, repository and service in the `user` module
- [ ] Registration and login, with BCrypt password hashing
- [ ] JWT issuing and validation; secret from the environment, never committed
- [ ] Spring Security configuration and filter chain
- [ ] Secure the company write endpoints (`POST`, `PUT`); keep reads public
- [ ] `401` / `403` mapped into the existing `ApiError` shape
- [ ] User profile endpoints

### Deferred from Step 3

- [ ] `PATCH /api/v1/companies/{id}/active` to hide a company from search,
      once there is an authenticated user allowed to press it
- [ ] Client-configurable sorting on company search (step 6, with an allowlist)
- [ ] Replace `LIKE '%term%'` with a `pg_trgm` GIN index (step 6)

## Future

Steps 4 to 15 in [ROADMAP.md](ROADMAP.md): authentication, jobs, search,
matching, frontend, dashboard, tracking, scan, OCR/AI, AI job intelligence,
production readiness, deployment. V2/V3 items are out of scope.
